import ij.*;
import ij.gui.*;

import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import ij.plugin.LutLoader;
import ij.plugin.*;
import ij.process.*;
import ij.ImagePlus;

public class Create_DVI_FromImage implements PlugIn {

	
	public void run(String arg) {
		ImagePlus imagePlus = WindowManager.getCurrentImage();
		String[] DVIBands = {"red", "green", "blue"};	
		// Get list of LUTs
		String lutLocation = IJ.getDirectory("luts");
		File lutDirectory = new File(lutLocation);
		String[] lutNames = lutDirectory.list();
		
		ImagePlus DVIImage = null;
		int redBand, irBand;
		Boolean saveParameters = true;
		Boolean useDefaults = false;
		
		// Initialize variables from IJ.Prefs file
		Boolean displayDVIColor = Prefs.get("pm.fromSBImage.createDVIColor", true);
		Boolean displayDVIFloat = Prefs.get("pm.fromSBImage.createDVIFloat", true);
		Boolean stretchVisible = Prefs.get("pm.fromSBImage.stretchVisible", true);
		Boolean stretchIR = Prefs.get("pm.fromSBImage.stretchIR", true);
		double saturatedPixels = Prefs.get("pm.fromSBImage.saturatedPixels", 2.0);
		double maxColorScale = Prefs.get("pm.fromSBImage.maxColorScale", 255.0);
		double minColorScale = Prefs.get("pm.fromSBImage.minColorScale", 0.0);
		String lutName = Prefs.get("pm.fromSBImage.lutName", lutNames[0]);
		int redBandIndex = (int)Prefs.get("pm.fromSBImage.redBandIndex", 2); 
		int irBandIndex = (int)Prefs.get("pm.fromSBImage.irBandIndex", 0);
		saturatedPixels = Prefs.get("pm.fromSBImage.saturatedPixels", 2.0);
		
		// Create dialog window
		GenericDialog dialog = new GenericDialog("Enter variables");
		dialog.addCheckbox("Load default parameters (click OK below to reload)", false);
		dialog.addCheckbox("Display Color DVI image?", displayDVIColor);
		dialog.addNumericField("Minimum DVI value for scaling color DVI image", minColorScale, 1);
		dialog.addNumericField("Maximum DVI value for scaling color DVI image", maxColorScale, 1);
		dialog.addCheckbox("Display floating point DVI image?", displayDVIFloat);
		dialog.addCheckbox("Stretch the visible band before creating DVI?", stretchVisible);
		dialog.addCheckbox("Stretch the NIR band before creating DVI?", stretchIR);
		dialog.addNumericField("Saturation value for stretch", saturatedPixels, 1);
		dialog.addChoice("Channel for Red band to create DVI", DVIBands, DVIBands[redBandIndex]);
		dialog.addChoice("Channel for IR band to create DVI", DVIBands, DVIBands[irBandIndex]);
		dialog.addChoice("Select output color table for color DVI image", lutNames, lutName);
		dialog.addCheckbox("Save parameters for next session", true);
		dialog.showDialog();
		if (dialog.wasCanceled()) {
			return;
		}
		
		useDefaults = dialog.getNextBoolean();
		if (useDefaults) {
			dialog = null;
			// Create dialog window with default values
			dialog = new GenericDialog("Enter variables");
			dialog.addCheckbox("Load default parameters (click OK below to reload)", false);
			dialog.addCheckbox("Output Color DVI image?", true);
			dialog.addNumericField("Enter the minimum DVI value for scaling color DVI image", -1.0, 1);
			dialog.addNumericField("Enter the maximum DVI value for scaling color DVI image", 1.0, 1);
			dialog.addCheckbox("Display floating point DVI image?", true);
			dialog.addCheckbox("Stretch the visible band before creating DVI?", true);
			dialog.addCheckbox("Stretch the NIR band before creating DVI?", true);
			dialog.addNumericField("Enter the saturation value for stretch", 2.0, 1);
			dialog.addChoice("Channel for Red band to create DVI", DVIBands, DVIBands[2]);
			dialog.addChoice("Channel for IR band to create DVI", DVIBands, DVIBands[0]);
			dialog.addChoice("Select output color table for color DVI image", lutNames, lutNames[0]);
			dialog.addCheckbox("Save parameters for next session", false);
			dialog.showDialog();
			if (dialog.wasCanceled()) {
				return;
			}
		}
		
		// Get variables from dialog
		if (useDefaults) { 
			dialog.getNextBoolean();
		}
		displayDVIColor = dialog.getNextBoolean();
		minColorScale = dialog.getNextNumber();
		maxColorScale = dialog.getNextNumber();
		displayDVIFloat = dialog.getNextBoolean();
		stretchVisible = dialog.getNextBoolean();
		stretchIR = dialog.getNextBoolean();
		saturatedPixels = dialog.getNextNumber();
		redBand = dialog.getNextChoiceIndex() + 1;
		irBand = dialog.getNextChoiceIndex() + 1;
		lutName = dialog.getNextChoice();	
		saveParameters = dialog.getNextBoolean();

		if (saveParameters) {
			// Set preferences to IJ.Prefs file
			Prefs.set("pm.fromSBImage.createDVIColor", displayDVIColor);
			Prefs.set("pm.fromSBImage.createDVIFloat", displayDVIFloat);
			Prefs.set("pm.fromSBImage.stretchVisible", stretchVisible);
			Prefs.set("pm.fromSBImage.stretchIR", stretchIR);
			Prefs.set("pm.fromSBImage.saturatedPixels", saturatedPixels);
			Prefs.set("pm.fromSBImage.maxColorScale", maxColorScale);
			Prefs.set("pm.fromSBImage.minColorScale", minColorScale);
			Prefs.set("pm.fromSBImage.lutName", lutName);
			Prefs.set("pm.fromSBImage.redBandIndex", redBand - 1);
			Prefs.set("pm.fromSBImage.irBandIndex", irBand - 1);
			Prefs.set("pm.fromSBImage.saturatedPixels", saturatedPixels);
		
			// Save preferences to IJ.Prefs file
			Prefs.savePreferences();
		}

		int numSlices = imagePlus.getImageStackSize();
		if (numSlices > 1) {
			ImageStack floatStack = null;
			ImageStack colorStack = null;
	    	if (displayDVIFloat) {
    			floatStack = new ImageStack(imagePlus.getWidth(), imagePlus.getHeight());
    		}
	    	if (displayDVIColor) {
	    		colorStack = new ImageStack(imagePlus.getWidth(), imagePlus.getHeight());
	    	}
			for (int sliceCount=1; sliceCount<=numSlices; sliceCount++) {
				ImageStack inputStack = imagePlus.getStack();
				ImagePlus sliceImagePlus = new ImagePlus("slice", inputStack.getProcessor(sliceCount)); // specify number of slice
				
				// Make sure images are RGB
				if (sliceImagePlus.getType() != ImagePlus.COLOR_RGB) {
		    		IJ.error("Images must be Color RGB");
		    		return;  
		    	}
				
				RegImagePair imagePair = new RegImagePair(sliceImagePlus, sliceImagePlus);
		    	DVIImage = imagePair.calcDVI(irBand, redBand, stretchVisible, stretchIR, saturatedPixels);
		    	
		    	if (displayDVIFloat) {
		    		floatStack.addSlice(DVIImage.getProcessor());
	    		}
		    	
		    	if (displayDVIColor) {
		    		IndexColorModel cm = null;
					LUT lut;
					//Uncomment next line to use default float-to-byte conversion
					//ImageProcessor colorDVI = DVIImage.getProcessor().convertToByte(true);
					ImagePlus colorDVI;
					colorDVI = NewImage.createByteImage("Color DVI", DVIImage.getWidth(), DVIImage.getHeight(), 1, NewImage.FILL_BLACK);
				
					float[] pixels = (float[])DVIImage.getProcessor().getPixels();
					for (int y=0; y<DVIImage.getHeight(); y++) {
						int offset = y*DVIImage.getWidth();
						for (int x=0; x<DVIImage.getWidth(); x++) {
							int pos = offset+x;
							colorDVI.getProcessor().putPixelValue(x, y, Math.round((pixels[pos] - minColorScale)/((maxColorScale - minColorScale) / 255.0)));
						}	    						    				
					}
					colorStack.addSlice(colorDVI.getProcessor());  
		    	}

			}
			if (displayDVIFloat) {
				ImagePlus imagePlusFloatStack = new ImagePlus("float stack", floatStack);
				imagePlusFloatStack.show();
			}
			if (displayDVIColor) {
	    		IndexColorModel cm = null;

	    		try {
	    			cm = LutLoader.open(lutLocation+lutName);
	    		} catch (IOException e) {
	    			IJ.error(""+e);
	    		}
				colorStack.setColorModel(cm);
				ImagePlus imagePlusColorStack = new ImagePlus("color stack", colorStack);
				imagePlusColorStack.show();
			}
			
		} else {
			RegImagePair imagePair = new RegImagePair(imagePlus, imagePlus);
			DVIImage = imagePair.calcDVI(irBand, redBand, stretchVisible, stretchIR, saturatedPixels);
			if (displayDVIFloat) {
				DVIImage.show();
			}
		
			if (displayDVIColor) {
				IndexColorModel cm = null;
				LUT lut;
				//Uncomment next line to use default float-to-byte conversion
				//ImageProcessor colorDVI = DVIImage.getProcessor().convertToByte(true);
				ImagePlus colorDVI;
				colorDVI = NewImage.createByteImage("Color DVI", DVIImage.getWidth(), DVIImage.getHeight(), 1, NewImage.FILL_BLACK);
			
				float[] pixels = (float[])DVIImage.getProcessor().getPixels();
				for (int y=0; y<DVIImage.getHeight(); y++) {
					int offset = y*DVIImage.getWidth();
					for (int x=0; x<DVIImage.getWidth(); x++) {
						int pos = offset+x;
						colorDVI.getProcessor().putPixelValue(x, y, Math.round((pixels[pos] - minColorScale)/((maxColorScale - minColorScale) / 255.0)));
					}	    						    				
				}
				// Get the LUT
				try {
					cm = LutLoader.open(lutLocation+lutName);
				} catch (IOException e) {
					IJ.error(""+e);
				}
		
				lut = new LUT(cm, 255.0, 0.0);
				colorDVI.getProcessor().setLut(lut);
				colorDVI.show();
			}
		}
	}
}
