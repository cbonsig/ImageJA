package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.measure.*;
import ij.gui.*;
import java.awt.*;
import java.util.*;

/** This plugin implements ImageJ's Unsharp Mask command. */
public class UnsharpMask implements PlugInFilter, Measurements {

	private ImagePlus imp;
	private int slice;
	private boolean canceled;
	private static double radius = 2;
	private static double weight = 0.6;
	private boolean isLineRoi;
	
	public int setup(String arg, ImagePlus imp) {
 		IJ.register(UnsharpMask.class);
		this.imp = imp;
		if (imp!=null) {
			IJ.resetEscape();
			Roi roi = imp.getRoi();
			isLineRoi= roi!=null && roi.isLine();
		}
		if (imp!=null && !showDialog())
			return DONE;
		else
			return IJ.setupDialog(imp, DOES_ALL);
	}

	public void run(ImageProcessor ip) {
		if (canceled)
			return;
		slice++;
		if (isLineRoi)
			ip.resetRoi();
		sharpen(ip, radius, weight);
		if (canceled) Undo.undo();
	}
	
	void showStatus(String msg) {
		if (slice>1)
			IJ.showStatus("Unsharp Mask: "+slice+"/"+imp.getStackSize());
		else
			IJ.showStatus("Unsharp Mask: " + msg);
	}
	
	public void sharpen(ImageProcessor ip, double radius, double weight) {
		ip.setCalibrationTable(null);
		Rectangle rect = ip.getRoi();
		boolean isRoi = rect.width!=ip.getWidth()||rect.height!=ip.getHeight();
		boolean nonRectRoi = ip.getMask()!=null;
		ImageProcessor ip2 = ip;
		if (isRoi) {
			ip2.setRoi(rect);
			showStatus("cropping");
			ip2 = ip2.crop();
		}
		if (ip instanceof ColorProcessor) {
			showStatus("extracting brightness");
			ip2 = ((ColorProcessor)ip2).getBrightness();
		} else {
			showStatus("converting to float");
			ip2 = ip2.convertToFloat();
		}
		if (abort()) return;
		showStatus("getting statistics");
		ImageStatistics stats = ImageStatistics.getStatistics(ip2, MIN_MAX, null);
		if (abort()) return;
		double min = stats.min;
		double max = stats.max;
		showStatus("Gaussian blur");
		ImageProcessor mask = ip2.duplicate();
		if (abort()) return;
		GaussianBlur gb = new GaussianBlur();
		if (!gb.blur(mask, radius))
			{canceled=true; IJ.beep(); return;}
		showStatus("Subtracting blurred mask");
		mask.multiply(weight);
		if (abort()) return;
		//new ImagePlus("", mask).show();
		ip2.copyBits(mask,0,0,Blitter.SUBTRACT);
		if (abort()) return;
		ip2.multiply(1.0/(1.0-weight));
		if (abort()) return;
		if (!(ip2 instanceof ColorProcessor)) {
			ip2.min(min);
			ip2.max(max);
		}
		showStatus("converting back");
		if (nonRectRoi)
			ip.snapshot();
		ImageProcessor ip3 = null;
		int bitDepth = imp.getBitDepth();
		boolean scale = bitDepth==8 && imp.getStackSize()==1;
		switch (bitDepth) {
			case 8: ip3 = ip2.convertToByte(scale); break;
			case 16: ip3 = ip2.convertToShort(scale); break;
			case 24: 
				ip3 = nonRectRoi||isRoi?ip.crop():ip;
				((ColorProcessor)ip3).setBrightness((FloatProcessor)ip2); 
				break;				
			case 32: ip3 = ip2; break;
		}
		ip.insert(ip3, rect.x, rect.y);
		if (nonRectRoi)
			ip.reset(ip.getMask());
	}
	
	boolean abort() {
		if (IJ.escapePressed()) {
			canceled=true; 
			IJ.beep(); 
			return true;
		} else
			return false;
	}
	
	public boolean showDialog() {
		GenericDialog gd = new GenericDialog("Unsharp Mask...");
		gd.addNumericField("Gaussian Radius (1-15)", radius,0);
		gd.addNumericField("Mask Weight (0.2-0.9)", weight,2);
		gd.showDialog();
		if (gd.wasCanceled()) {
			canceled = true;
			return false;
		}
		radius = gd.getNextNumber();
		weight = gd.getNextNumber();
		return true;
	}

}


