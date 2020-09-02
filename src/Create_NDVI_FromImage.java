import ij.*;
import ij.gui.*;

import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import ij.plugin.*;
import ij.plugin.frame.RoiManager;
import ij.process.*;
import ij.ImagePlus;
import ij.io.FileInfo;

public class Create_NDVI_FromImage implements PlugIn {

	
	public void run(String arg) {
		ImagePlus imagePlus = WindowManager.getCurrentImage();
		if (imagePlus == null) {
			IJ.error("This plugin requires that an image is displayed. Please open an image and try again");
	    	return;
		}
		
		String[] indexTypes = {"NDVI: (NIR-Vis)/(NIR+Vis)", "DVI: NIR-Vis"};
		String[] IndexBands = {"red", "green", "blue"};	
		// Get list of LUTs
		String lutLocation = IJ.getDirectory("luts");
		File lutDirectory = new File(lutLocation);
		String[] lutNames = lutDirectory.list();
		
		ImagePlus indexImage = null;
		int redBand, irBand;
		Boolean saveParameters = true;
		Boolean useDefaults = false;
		
		// Initialize variables from IJ.Prefs file
		String indexType = Prefs.get("pm.fromSBImage.indexType", indexTypes[0]);
		Boolean displayIndexColor = Prefs.get("pm.fromSBImage.createIndexColor", true);
		Boolean displayIndexFloat = Prefs.get("pm.fromSBImage.createIndexFloat", true);
		Boolean stretchVisible = Prefs.get("pm.fromSBImage.stretchVisible", true);
		Boolean stretchIR = Prefs.get("pm.fromSBImage.stretchIR", true);
		double saturatedPixels = Prefs.get("pm.fromSBImage.saturatedPixels", 2.0);
		double maxColorScale = Prefs.get("pm.fromSBImage.maxColorScale", 1.0);
		double minColorScale = Prefs.get("pm.fromSBImage.minColorScale", -1.0);
		String lutName = Prefs.get("pm.fromSBImage.lutName", lutNames[0]);
		int redBandIndex = (int)Prefs.get("pm.fromSBImage.redBandIndex", 2); 
		int irBandIndex = (int)Prefs.get("pm.fromSBImage.irBandIndex", 0);
		saturatedPixels = Prefs.get("pm.fromSBImage.saturatedPixels", 2.0);
		
		// Create dialog window
		GenericDialog dialog = new GenericDialog("Enter variables");
		dialog.addCheckbox("Load default parameters (click OK below to reload)", false);
		dialog.addChoice("Select index type for calculation", indexTypes, indexType);
		dialog.addCheckbox("Display Color Index image?", displayIndexColor);
		dialog.addNumericField("Minimum Index value for scaling color Index image", minColorScale, 2);
		dialog.addNumericField("Maximum Index value for scaling color Index image", maxColorScale, 2);
		dialog.addCheckbox("Display floating point Index image?", displayIndexFloat);
		dialog.addCheckbox("Stretch the visible band before creating Index?", stretchVisible);
		dialog.addCheckbox("Stretch the NIR band before creating Index?", stretchIR);
		dialog.addNumericField("Saturation value for stretch", saturatedPixels, 1);
		dialog.addChoice("Channel for Red band to create Index", IndexBands, IndexBands[redBandIndex]);
		dialog.addChoice("Channel for IR band to create Index", IndexBands, IndexBands[irBandIndex]);
		dialog.addChoice("Select output color table for color Index image", lutNames, lutName);
		dialog.addCheckbox("Save parameters for next session", true);
		dialog.showDialog();
		if (dialog.wasCanceled()) {
			return;
		}
		
		useDefaults = dialog.getNextBoolean();
		if (useDefaults) {
			dialog = null;
			// Create dialog window with default values
			dialog = new GenericDialog("Enter variables");;
			dialog.addCheckbox("Load default parameters (click OK below to reload)", false);
			dialog.addChoice("Select index type for calculation", indexTypes, indexTypes[0]);
			dialog.addCheckbox("Output Color Index image?", true);
			dialog.addNumericField("Enter the minimum Index value for scaling color Index image", -1.0, 2);
			dialog.addNumericField("Enter the maximum Index value for scaling color Index image", 1.0, 2);
			dialog.addCheckbox("Display floating point Index image?", true);
			dialog.addCheckbox("Stretch the visible band before creating Index?", true);
			dialog.addCheckbox("Stretch the NIR band before creating Index?", true);
			dialog.addNumericField("Enter the saturation value for stretch", 2.0, 1);
			dialog.addChoice("Channel for Red band to create Index", IndexBands, IndexBands[2]);
			dialog.addChoice("Channel for IR band to create Index", IndexBands, IndexBands[0]);
			dialog.addChoice("Select output color table for color Index image", lutNames, lutNames[0]);
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
		indexType = dialog.getNextChoice();
		displayIndexColor = dialog.getNextBoolean();
		minColorScale = dialog.getNextNumber();
		maxColorScale = dialog.getNextNumber();
		displayIndexFloat = dialog.getNextBoolean();
		stretchVisible = dialog.getNextBoolean();
		stretchIR = dialog.getNextBoolean();
		saturatedPixels = dialog.getNextNumber();
		redBand = dialog.getNextChoiceIndex() + 1;
		irBand = dialog.getNextChoiceIndex() + 1;
		lutName = dialog.getNextChoice();	
		saveParameters = dialog.getNextBoolean();

		if (saveParameters) {
			// Set preferences to IJ.Prefs file
			Prefs.set("pm.fromSBImage.indexType", indexType);
			Prefs.set("pm.fromSBImage.createIndexColor", displayIndexColor);
			Prefs.set("pm.fromSBImage.createIndexFloat", displayIndexFloat);
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
	    	if (displayIndexFloat) {
    			floatStack = new ImageStack(imagePlus.getWidth(), imagePlus.getHeight());
    		}
	    	if (displayIndexColor) {
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
		    	if (indexType == indexTypes[0]) {
		    		indexImage = imagePair.calcNDVI(irBand, redBand, stretchVisible, stretchIR, saturatedPixels);
		    	} 
		    	else if (indexType == indexTypes[1]) {
		    		indexImage = imagePair.calcDVI(irBand, redBand, stretchVisible, stretchIR, saturatedPixels);
		    	}
		    	
		    	if (displayIndexFloat) {
		    		floatStack.addSlice(indexImage.getProcessor());
	    		}
		    	
		    	if (displayIndexColor) {
		    		//IndexColorModel cm = null;
					//LUT lut;
					//Uncomment next line to use default float-to-byte conversion
					//ImageProcessor colorNDVI = ndviImage.getProcessor().convertToByte(true);
					ImagePlus colorIndex;
					colorIndex = NewImage.createByteImage("Color Index", indexImage.getWidth(), indexImage.getHeight(), 1, NewImage.FILL_BLACK);
				
					float[] pixels = (float[])indexImage.getProcessor().getPixels();
					for (int y=0; y<indexImage.getHeight(); y++) {
						int offset = y*indexImage.getWidth();
						for (int x=0; x<indexImage.getWidth(); x++) {
							int pos = offset+x;
							colorIndex.getProcessor().putPixelValue(x, y, Math.round((pixels[pos] - minColorScale)/((maxColorScale - minColorScale) / 255.0)));
						}	    						    				
					}
					colorStack.addSlice(colorIndex.getProcessor());  
		    	}

			}
			if (displayIndexFloat) {
				ImagePlus imagePlusFloatStack = new ImagePlus("float stack", floatStack);
				imagePlusFloatStack.show();
			}
			if (displayIndexColor) {
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
	    	// Test metadata reader
	    	//double[] latLon = new double[2];
	    	//String fileName = imagePlus.getOriginalFileInfo().fileName;
	    	//String fileDir = imagePlus.getOriginalFileInfo().directory;
	    	//File imageFile = new File(fileDir+fileName);
	    	//MetadataReader metaReader = new MetadataReader(imageFile);
	    	//latLon = metaReader.getLatLon();
			
			RegImagePair imagePair = new RegImagePair(imagePlus, imagePlus);
			if (indexType == indexTypes[0]) {
	    		indexImage = imagePair.calcNDVI(irBand, redBand, stretchVisible, stretchIR, saturatedPixels);
	    	} 
	    	else if (indexType == indexTypes[1]) {
	    		indexImage = imagePair.calcDVI(irBand, redBand, stretchVisible, stretchIR, saturatedPixels);
	    	}

			if (displayIndexFloat) {
				indexImage.show();
			}
		
			if (displayIndexColor) {
				IndexColorModel cm = null;
				LUT lut;
				//Uncomment next line to use default float-to-byte conversion
				//ImageProcessor colorNDVI = ndviImage.getProcessor().convertToByte(true);
				ImagePlus colorIndex;
				colorIndex = NewImage.createByteImage("Color Index", indexImage.getWidth(), indexImage.getHeight(), 1, NewImage.FILL_BLACK);
			
				float[] pixels = (float[])indexImage.getProcessor().getPixels();
				for (int y=0; y<indexImage.getHeight(); y++) {
					int offset = y*indexImage.getWidth();
					for (int x=0; x<indexImage.getWidth(); x++) {
						int pos = offset+x;
						colorIndex.getProcessor().putPixelValue(x, y, Math.round((pixels[pos] - minColorScale)/((maxColorScale - minColorScale) / 255.0)));
					}	    						    				
				}
				// Get the LUT
				try {
					cm = LutLoader.open(lutLocation+lutName);
				} catch (IOException e) {
					IJ.error(""+e);
				}
		
				lut = new LUT(cm, 255.0, 0.0);
				colorIndex.getProcessor().setLut(lut);
				colorIndex.show();
			}
		}
	}
}
