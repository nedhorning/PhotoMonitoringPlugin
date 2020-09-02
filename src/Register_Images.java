import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.plugin.*;
import ij.process.ImageConverter;
import ij.process.*;
import ij.gui.DialogListener;
import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.AWTEvent;
import java.lang.Math;

public class Register_Images implements PlugIn, DialogListener {
	static final int ADD=1, SUBTRACT=2, MULTIPLY=3, DIVIDE=4;
	public void run(String arg) {
		// Get list of LUTs
		String lutLocation = IJ.getDirectory("luts");
		File lutDirectory = new File(lutLocation);
		String[] lutNames = lutDirectory.list();
		// Dialog variables
		String[] indexTypes = {"NDVI: (NIR-Vis)/(NIR+Vis)", "DVI: NIR-Vis"};
		String[] primaryRegMethodTypes = {"SIFT/Landmark correspondences", "SIFT/Landmark correspondences using reference points from first valid image pair", "bUnwarpJ"};
		String[] secondaryRegMethodTypes = {"SIFT/Landmark correspondences", "SIFT/Landmark correspondences using last valid set of points", "bUnwarpJ"};
		String[] transformationTypes = {"Rigid", "Affine"};
		String[] preprocessingType = {"nir (g+b) vis (g-b)", "nir=green band vis=green band", "none"}; 
		String[] IndexBands = {"red", "green", "blue"};
		String[] exifImageArray = {"visible", "nir"};
		WriteEXIF exifWriter = null;
		File outFile = null;
		File tempFile = null;
		File inImage = null;
		ImagePlus rawSourceImage = null;
		ImagePlus rawTargetImage = null;
		ImagePlus regSource = null;
		RegImagePair regImages = null;
		ImagePlus indexImage = null;
		String indexType = Prefs.get("pm.fromSBImage.indexType", indexTypes[0]);
		String primaryRegMethod = Prefs.get("pm.reg.primaryRegMethod", primaryRegMethodTypes[0]);
		String secondaryRegMethod = Prefs.get("pm.reg.secondaryRegMethod", secondaryRegMethodTypes[1]);
		String transformation = Prefs.get("pm.reg.transformation", transformationTypes[0]);
		String preprocessingMethod = Prefs.get("pm.reg.preprocessingMethod", preprocessingType[2]);
		Boolean useSecondaryMethod = Prefs.get("pm.reg.useSecondaryMethod", true);
		int numSiftTries = (int)Prefs.get("pm.reg.numSiftTries", 1);
		Boolean createNRG = Prefs.get("pm.reg.createNRG", true);
		Boolean clipImages = Prefs.get("pm.reg.clipImages", true);
		Boolean createIndexColor = Prefs.get("pm.reg.createIndexColor", true);
		Boolean createIndexFloat = Prefs.get("pm.reg.createIndexFloat", true);
		Boolean outputClipTwo = Prefs.get("pm.reg.outputClipTwo", true);
		Boolean stretchVisible = Prefs.get("pm.reg.stretchVisible", true);
		Boolean stretchIR = Prefs.get("pm.reg.stretchIR", true);
		double saturatedPixels = Prefs.get("pm.reg.saturatedPixels", 2.0);
		double maxColorScale = Prefs.get("pm.reg.maxColorScale", 1.0);
		double minColorScale = Prefs.get("pm.reg.minColorScale", -1.0);;
		String exifImageReference = Prefs.get("pm.reg.exifImageReference", exifImageArray[0]);
		String lutName = Prefs.get("pm.reg.lutName", lutNames[0]);
		String logName = "log.txt";
		String outFileBase = "";
		boolean continueProcessing = true;
		int redBandIndex = (int)Prefs.get("pm.reg.redBandIndex", 0); 
		int irBandIndex = (int)Prefs.get("pm.reg.irBandIndex", 2);
		int redBand, irBand;
		RoiPair lastGoodRois = null;
		Boolean saveParameters = true;
		Boolean useDefaults = false;
		

		// Create dialog window
		GenericDialog dialog = new GenericDialog("Enter variables");
		dialog.addCheckbox("Load default parameters (click OK below to reload)", false);
		dialog.addMessage("Image-to-image registration options:");
		dialog.addCheckbox("Use backup registration method if primary fails?", useSecondaryMethod);
		dialog.addChoice("Select primary registration method", primaryRegMethodTypes, primaryRegMethod);
		dialog.addChoice("Select secondary registration method", secondaryRegMethodTypes, secondaryRegMethod);
		dialog.addChoice("Select transformation type if using SIFT", transformationTypes, transformation);
		dialog.addNumericField("Number of tries for SIFT to find correspondence points", numSiftTries, 0);
		dialog.addChoice("Method to improve SIFT point selection", preprocessingType, preprocessingMethod);
		dialog.addMessage("Output image options:");
		dialog.addChoice("Select index type for calculation", indexTypes, indexType);
		dialog.addChoice("Image to copy EXIF metadata from", exifImageArray, exifImageReference);
		dialog.addCheckbox("Output NRG image?", createNRG);
		dialog.addCheckbox("Clip images?", clipImages);
		dialog.addCheckbox("Output clipped visible image?", outputClipTwo);
		dialog.addCheckbox("Output Color Index image?", createIndexColor);
		dialog.addNumericField("Minimum Index value for scaling color Index image", minColorScale, 2);
		dialog.addNumericField("Maximum Index value for scaling color Index image", maxColorScale, 2);
		dialog.addCheckbox("Output floating point Index image?", createIndexFloat);
		dialog.addCheckbox("Stretch the visible band before creating Index?", stretchVisible);
		dialog.addCheckbox("Stretch the NIR band before creating Index?", stretchIR);
		dialog.addNumericField("Saturation value for stretch", saturatedPixels, 1);
		dialog.addChoice("Channel from visible image to use for Red band to create Index", IndexBands, IndexBands[redBandIndex]);
		dialog.addChoice("Channel from IR image to use for IR band to create Index", IndexBands, IndexBands[irBandIndex]);
		dialog.addChoice("Select output color table for color Index image", lutNames, lutName);
		dialog.addCheckbox("Save parameters for next session", true);
		dialog.addDialogListener(this);
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
			dialog.addMessage("Image-to-image registration options:");
			dialog.addCheckbox("Use backup registration method if primary fails?", true);
			dialog.addChoice("Select primary registration method", primaryRegMethodTypes, primaryRegMethodTypes[0]);
			dialog.addChoice("Select secondary registration method", secondaryRegMethodTypes, secondaryRegMethodTypes[1]);
			dialog.addChoice("Select transformation type if using SIFT", transformationTypes, transformationTypes[0]);
			dialog.addNumericField("Number of tries for SIFT to find correspondence points", 1, 0);
			dialog.addChoice("Method to improve SIFT point selection", preprocessingType, preprocessingType[2]);
			dialog.addMessage("Output image options:");
			dialog.addChoice("Select index type for calculation", indexTypes, indexTypes[0]);
			dialog.addChoice("Image to copy EXIF metadata from", exifImageArray, exifImageArray[0]);
			dialog.addCheckbox("Output NRG image?", true);
			dialog.addCheckbox("Clip images?", true);
			dialog.addCheckbox("Output clipped visible image?", true);
			dialog.addCheckbox("Output Color Index image?", true);
			dialog.addNumericField("Minimum Index value for scaling color Index image", -1.0, 2);
			dialog.addNumericField("Maximum Index value for scaling color Index image", 1.0, 2);
			dialog.addCheckbox("Output floating point Index image?", true);
			dialog.addCheckbox("Stretch the visible band before creating Index?", true);
			dialog.addCheckbox("Stretch the NIR band before creating Index?", true);
			dialog.addNumericField("Saturation value for stretch", 2.0, 1);
			dialog.addChoice("Channel from visible image to use for Red band to create Index", IndexBands, IndexBands[0]);
			dialog.addChoice("Channel from IR image to use for IR band to create Index", IndexBands, IndexBands[2]);
			dialog.addChoice("Select output color table for color Index image", lutNames, lutNames[0]);
			dialog.addCheckbox("Save parameters for next session", false);
			dialog.addDialogListener(this);
			dialog.showDialog();
			if (dialog.wasCanceled()) {
				return;		
			}
		}
		
		// Get variables from dialog
		if (useDefaults) { 
			dialog.getNextBoolean();
		}
		useSecondaryMethod = dialog.getNextBoolean();
		primaryRegMethod = dialog.getNextChoice();
		secondaryRegMethod = dialog.getNextChoice();
		transformation = dialog.getNextChoice();
		numSiftTries = (int)dialog.getNextNumber();
		preprocessingMethod = dialog.getNextChoice();
		indexType = dialog.getNextChoice();
		exifImageReference = dialog.getNextChoice();
		createNRG = dialog.getNextBoolean();
		clipImages = dialog.getNextBoolean();
		outputClipTwo = dialog.getNextBoolean();
		createIndexColor = dialog.getNextBoolean();
		minColorScale = dialog.getNextNumber();
		maxColorScale = dialog.getNextNumber();
		createIndexFloat = dialog.getNextBoolean();
		stretchVisible = dialog.getNextBoolean();
		stretchIR = dialog.getNextBoolean();
		saturatedPixels = dialog.getNextNumber();
		redBand = dialog.getNextChoiceIndex() + 1;
		irBand = dialog.getNextChoiceIndex() + 1;
		lutName  = dialog.getNextChoice();
		saveParameters  = dialog.getNextBoolean();
		
		// Set preferences to IJ.Prefs file
		if (saveParameters) {
			Prefs.set("pm.reg.useSecondaryRegMethod", useSecondaryMethod);
			Prefs.set("pm.reg.primaryRegMethod", primaryRegMethod);
			Prefs.set("pm.reg.secondaryRegMethod", secondaryRegMethod);
			Prefs.set("pm.reg.transformation", transformation);
			Prefs.set("pm.reg.numSiftTries", numSiftTries);
			Prefs.set("pm.reg.preprocessingMethod", preprocessingMethod);
			Prefs.set("pm.fromSBImage.indexType", indexType);
			Prefs.set("pm.reg.exifImageReference", exifImageReference);
			Prefs.set("pm.reg.createNRG", createNRG);
			Prefs.set("pm.reg.clipImages", clipImages);
			Prefs.set("pm.reg.outputClipTwo", outputClipTwo);
			Prefs.set("pm.reg.createIndexColor", createIndexColor);
			Prefs.set("pm.reg.minColorScale", minColorScale);
			Prefs.set("pm.reg.maxColorScale", maxColorScale);
			Prefs.set("pm.reg.createIndexFloat", createIndexFloat);
			Prefs.set("pm.reg.stretchVisible", stretchVisible);
			Prefs.set("pm.reg.stretchIR", stretchIR);
			Prefs.set("pm.reg.saturatedPixels", saturatedPixels);
			Prefs.set("pm.reg.redBandIndex", redBand - 1);
			Prefs.set("pm.reg.irBandIndex", irBand - 1);
			Prefs.set("pm.reg.lutName", lutName);
		
			// Save preferences to IJ.Prefs file
			Prefs.savePreferences();
		}
		
		// Dialog for photo pair list file
		OpenDialog od = new OpenDialog("Photo pair file", arg);
	    String pairDirectory = od.getDirectory();
	    String pairFileName = od.getFileName();
	    if (pairFileName==null) {
	    	IJ.error("No file was selected");
	    	return;
	    }
		
		// Dialog for output photos directory and log file name
		SaveDialog sd = new SaveDialog("Output directory and log file name", "log", ".txt");
	    String outDirectory = sd.getDirectory();
	    logName = sd.getFileName();
	    if (logName==null){
	    	IJ.error("No directory was selected");
	    	return;
	    }
	    
	    // Get photoPairs
	    FilePairList photoPairs = new FilePairList(pairDirectory, pairFileName);
	    
	    // Start processing one image pair at a time
	    try {
	    	BufferedWriter bufWriter = new BufferedWriter(new FileWriter(outDirectory+logName));
	    	
	    	// Write out parameter settings to log file
	    	bufWriter.write("PARAMETER SETTINGS:\n");
	    	bufWriter.write("Use backup registration method if primary fails? " + useSecondaryMethod + "\n");
		    bufWriter.write("Select primary registration method: " + primaryRegMethod + "\n");
		    bufWriter.write("Select secondary registration method: " + secondaryRegMethod + "\n");
		    bufWriter.write("Select transformation type if using SIFT: " + transformation + "\n");
		    bufWriter.write("Number of tries for SIFT to find correspondence points: " + numSiftTries + "\n");
		    bufWriter.write("Method to improve SIFT point selection: " + preprocessingMethod + "\n");
		    bufWriter.write("Select index type for calculation: " + indexType + "\n\n");
		    bufWriter.write("Output NRG image? " + createNRG + "\n");
		    bufWriter.write("Clip images? " + clipImages + "\n");
		    bufWriter.write("Output clipped visible image? " + outputClipTwo + "\n");
		    bufWriter.write("Output Color Index image? " + createIndexColor + "\n");
		    bufWriter.write("Minimum Index value for scaling color Index image: " + minColorScale + "\n");
		    bufWriter.write("Maximum Index value for scaling color Index image: " + maxColorScale + "\n");
		    bufWriter.write("Output floating point Index image? " + createIndexFloat + "\n");
		    bufWriter.write("Stretch the visible band before creating Index? " + stretchVisible + "\n");
		    bufWriter.write("Stretch the NIR band before creating Index? " + stretchIR + "\n");
		    bufWriter.write("Saturation value for stretch: " + saturatedPixels + "\n");
		    bufWriter.write("Channel from visible image to use for Red band to create Index: " + redBand + "\n");
		    bufWriter.write("Channel from IR image to use for IR band to create Index: " + irBand + "\n");
		    bufWriter.write("Select output color table for color Index image: " + lutName + "\n\n");
		    bufWriter.write("PHOTO PAIR PROCESSING SETTINGS:\n");
	    
	    	for (FilePair filePair : photoPairs) {
	    		// Open image pairs
	    		continueProcessing = false;
	    	
	    		rawSourceImage = new ImagePlus(filePair.getFirst().trim());
	    		rawTargetImage = new ImagePlus(filePair.getSecond().trim());
	    		
	    		outFileBase = rawTargetImage.getTitle().replaceFirst("[.][^.]+$", "");

	    		// If the input image is 3-band integer image ocnvert it to "COLOR_RGB"
	    		if (rawSourceImage.getType() != ImagePlus.COLOR_RGB) {
	    			ImagePlus[] sourceImageBands = ChannelSplitter.split(rawSourceImage);
	    			ImageConverter redConverter = new ImageConverter(sourceImageBands[0]);
	    			ImageConverter greenConverter = new ImageConverter(sourceImageBands[1]);
	    			ImageConverter blueConverter = new ImageConverter(sourceImageBands[2]);
	    			redConverter.convertToGray8();
	    			greenConverter.convertToGray8();
	    			blueConverter.convertToGray8();
	    			rawSourceImage = NewImage.createRGBImage("ColorImage", rawSourceImage.getWidth(), rawSourceImage.getHeight(), 1, NewImage.FILL_BLACK);
	    			ImageProcessor sourceImage_ip = rawSourceImage.getProcessor();
	    			int[] pixels = (int[]) sourceImage_ip.getPixels();
	    			for (int y=0; y<rawSourceImage.getHeight(); y++) {
	    	            int offset = y*rawSourceImage.getWidth();
	    				for (int x=0; x<rawSourceImage.getWidth(); x++) {
	    					int pos = offset+x;
	    					pixels[pos] = ((sourceImageBands[0].getProcessor().getPixel(x,y) & 0xff) << 16) + ((sourceImageBands[1].getProcessor().getPixel(x,y) & 0xff) << 8) + (sourceImageBands[2].getProcessor().getPixel(x,y) & 0xff);	 
	    				}
	    			}
	    			rawSourceImage.updateImage();
	    		}
	    		
	    		if (rawTargetImage.getType() != ImagePlus.COLOR_RGB) {
	    			ImagePlus[] targetImageBands = ChannelSplitter.split(rawTargetImage);
	    			ImageConverter redConverter = new ImageConverter(targetImageBands[0]);
	    			ImageConverter greenConverter = new ImageConverter(targetImageBands[1]);
	    			ImageConverter blueConverter = new ImageConverter(targetImageBands[2]);
	    			redConverter.convertToGray8();
	    			greenConverter.convertToGray8();
	    			blueConverter.convertToGray8();
	    			rawTargetImage = NewImage.createRGBImage("ColorImage", rawSourceImage.getWidth(), rawSourceImage.getHeight(), 1, NewImage.FILL_BLACK);
	    			ImageProcessor targetImage_ip = rawTargetImage.getProcessor();
	    			int[] pixels = (int[]) targetImage_ip.getPixels();
	    			for (int y=0; y<rawSourceImage.getHeight(); y++) {
	    	            int offset = y*rawSourceImage.getWidth();
	    				for (int x=0; x<rawSourceImage.getWidth(); x++) {
	    					int pos = offset+x;
	    					pixels[pos] = ((targetImageBands[0].getProcessor().getPixel(x,y) & 0xff) << 16) + ((targetImageBands[1].getProcessor().getPixel(x,y) & 0xff) << 8) + (targetImageBands[2].getProcessor().getPixel(x,y) & 0xff);	   
	    				}
	    			}
	    			rawTargetImage.updateImage();
	    		}
  	
	    		rawSourceImage.show();
	    		rawTargetImage.show();
	
	    		if (primaryRegMethod == primaryRegMethodTypes[0] ||  (primaryRegMethod == primaryRegMethodTypes[1]) && lastGoodRois == null) {
	    			RoiPair rois = doSift(rawSourceImage, rawTargetImage, numSiftTries, transformation, preprocessingMethod);
	    			if (rois != null) {
	    				lastGoodRois = new RoiPair(rois.getSourceRoi(), rois.getTargetRoi());
	    				rawSourceImage.setRoi(rois.getSourceRoi(), false);
	    				rawTargetImage.setRoi(rois.getTargetRoi(), false);
	    				regSource = doLandmark(rawSourceImage, rawTargetImage, transformation);
	    				bufWriter.write("Images "+rawTargetImage.getTitle()+" and "+rawSourceImage.getTitle()+" " +
	    					"registered using SIFT and landmark correspondences with "+transformation+" transformation\n");
	    				continueProcessing = true;
	    			} 
	    			else {
	    				continueProcessing = false;
	    			}
	    		}
	    		else if (primaryRegMethod == primaryRegMethodTypes[1] && lastGoodRois != null) {
		    		rawSourceImage.setRoi(lastGoodRois.getSourceRoi(), false);
	    			rawTargetImage.setRoi(lastGoodRois.getTargetRoi(), false);
	    			regSource = doLandmark(rawSourceImage, rawTargetImage, transformation);
	    			if (regSource != null) {
	    				bufWriter.write("Images "+rawTargetImage.getTitle()+" and "+rawSourceImage.getTitle()+" " +
	    						"registered using previous SIFT points and landmark correspondences with "+transformation+" transformation\n");
	    				continueProcessing = true;
	    			}
	    			else {
	    				continueProcessing = false;
	    			}
	    		} else if (primaryRegMethod == primaryRegMethodTypes[2]) {
	    			regSource = dobUnwarpJ(rawSourceImage, rawTargetImage, transformation);
	    			if (regSource != null) {
	    				bufWriter.write("Images "+rawTargetImage.getTitle()+" and "+rawSourceImage.getTitle()+
    							" registered using bUnwarpJ\n");
	    				continueProcessing = true;
	    			}
	    			else {
	    				continueProcessing = false;
	    			}
	    		}
	    		
	    		if (!continueProcessing && useSecondaryMethod) {
		    		if (secondaryRegMethod == secondaryRegMethodTypes[0] ||  (secondaryRegMethod == secondaryRegMethodTypes[1]) && lastGoodRois == null) {
		    			RoiPair rois = doSift(rawSourceImage, rawTargetImage, numSiftTries, transformation, preprocessingMethod);
		    			if (rois != null) {
		    				lastGoodRois = new RoiPair(rois.getSourceRoi(), rois.getTargetRoi());
		    				rawSourceImage.setRoi(rois.getSourceRoi(), false);
		    				rawTargetImage.setRoi(rois.getTargetRoi(), false);
		    				regSource = doLandmark(rawSourceImage, rawTargetImage, transformation);
		    				bufWriter.write("Images "+rawTargetImage.getTitle()+" and "+rawSourceImage.getTitle()+" " +
		    					"registered using SIFT and landmark correspondences with "+transformation+" transformation\n");
		    				continueProcessing = true;
		    			} 
		    			else {
		    				continueProcessing = false;
		    			}
		    		}
		    		else if (secondaryRegMethod == secondaryRegMethodTypes[1] && lastGoodRois != null) {
			    		rawSourceImage.setRoi(lastGoodRois.getSourceRoi(), false);
		    			rawTargetImage.setRoi(lastGoodRois.getTargetRoi(), false);
		    			regSource = doLandmark(rawSourceImage, rawTargetImage, transformation);
		    			if (regSource != null) {
		    				bufWriter.write("Images "+rawTargetImage.getTitle()+" and "+rawSourceImage.getTitle()+" " +
		    						"registered using previous SIFT points and landmark correspondences with "+transformation+" transformation\n");
		    				continueProcessing = true;
		    			}
		    			else {
		    				continueProcessing = false;
		    			}
		    		} else if (secondaryRegMethod == secondaryRegMethodTypes[2]) {
		    			regSource = dobUnwarpJ(rawSourceImage, rawTargetImage, transformation);
		    			if (regSource != null) {
		    				bufWriter.write("Images "+rawTargetImage.getTitle()+" and "+rawSourceImage.getTitle()+
	    							" registered using bUnwarpJ\n");
		    				continueProcessing = true;
		    			}
		    			else {
		    				continueProcessing = false;
		    			}
		    		}
		    	}
	    		bufWriter.flush();
    			if (exifImageReference==exifImageArray[0]) {
    				inImage = new File(filePair.getSecond().trim());
    			}
    			else {
    				inImage = new File(filePair.getFirst().trim());
    			}
	    
	    		if (continueProcessing) {
	    			if (clipImages) {
	    				regImages = clipPair(regSource, rawTargetImage);
	    			}
	    			else {
	    				regImages = new RegImagePair(regSource, rawTargetImage);
	    			}
	    			if (createIndexFloat | createIndexColor) {
	    				if (indexType == indexTypes[0]) {
	    					indexImage = regImages.calcNDVI(irBand, redBand, stretchVisible, stretchIR, saturatedPixels);
	    				} 
	    				else if (indexType == indexTypes[1]) {
	    					indexImage = regImages.calcDVI(irBand, redBand, stretchVisible, stretchIR, saturatedPixels);
	    				}
	    			
	    			}
	    			
	    			if (createIndexFloat) {
	    				if (indexType == indexTypes[0]) {
	    					IJ.save(indexImage, outDirectory+outFileBase+"_NDVI_Float.tif");
	    				}
	    				else if (indexType == indexTypes[1]) {
	    					IJ.save(indexImage, outDirectory+outFileBase+"_DVI_Float.tif");
	    				}
	    			}
	    			if (createIndexColor) {
	    				IndexColorModel cm = null;
	    				LUT lut;
	    				// Uncomment next line to use default float-to-byte conversion
	    				//ImageProcessor colorNDVI = ndviImage.getProcessor().convertToByte(true);
	    				ImagePlus colorIndex = null;
	    				
	    				if (indexType == indexTypes[0]) {
	    					colorIndex = NewImage.createByteImage("Color NDVI", indexImage.getWidth(), indexImage.getHeight(), 1, NewImage.FILL_BLACK);
	    				}
	    				else if (indexType == indexTypes[1]) {
	    					colorIndex = NewImage.createByteImage("Color DVI", indexImage.getWidth(), indexImage.getHeight(), 1, NewImage.FILL_BLACK);
	    				}
	    				
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
	    				//ImagePlus colorNDVI_Image = new ImagePlus("Color NDVI", colorNDVI);
	    				String tempFileName = outDirectory+outFileBase+"IndexColorTemp."+"jpg";
	    				tempFile = new File(tempFileName);
	    				IJ.save(colorIndex, tempFileName);
	    				if (indexType == indexTypes[0]) {
	    					//IJ.save(colorIndex, outDirectory+outFileBase+"_NDVI_Color."+fileType);
	    					outFile = new File(outDirectory+outFileBase+"_NDVI_Color."+"jpg");
	    				}
	    				else if (indexType == indexTypes[1]) {
	    					//IJ.save(colorIndex, outDirectory+outFileBase+"_DVI_Color."+fileType);
	    					outFile = new File(outDirectory+outFileBase+"_DVI_Color."+"jpg");
	    				}
	    			}

	    			exifWriter = new WriteEXIF(inImage, outFile, tempFile);
					exifWriter.copyEXIF();
	    	
	    			if (outputClipTwo) {
	    				//IJ.save(regImages.getSecond(), outDirectory+outFileBase+"_Clipped."+fileType);
	    				String tempFileName = outDirectory+outFileBase+"ClippedTemp.jpg";
	    				tempFile = new File(tempFileName);
	    				IJ.save(regImages.getSecond(), tempFileName);
	    				outFile = new File(outDirectory+outFileBase+"_Clipped.jpg");
	    				inImage = new File(filePair.getSecond().trim());  // To make sure EXIF comes from visible image
	    		    	exifWriter = new WriteEXIF(inImage, outFile, tempFile);
	    				exifWriter.copyEXIF();
	    			}
	    	
	    			if (createNRG) {
	    				ColorProcessor firstCP = (ColorProcessor)regImages.getFirst().getProcessor();
	    				ColorProcessor secondCP = (ColorProcessor)regImages.getSecond().getProcessor();
	    				ColorProcessor colorNRG = new ColorProcessor(regImages.getFirst().getWidth(), regImages.getSecond().getHeight());
	    				colorNRG.setChannel(1, firstCP.getChannel(1, null));
	    				colorNRG.setChannel(2, secondCP.getChannel(1, null));
	    				colorNRG.setChannel(3, secondCP.getChannel(2, null));
	    				ImagePlus nrgImage = new ImagePlus("NRG Image", colorNRG);
	    				//IJ.save(nrgImage, outDirectory+outFileBase+"_NRG."+fileType);
	    				String tempFileName = outDirectory+outFileBase+"NRGTemp.jpg";
	    				tempFile = new File(tempFileName);
	    				IJ.save(nrgImage, tempFileName);
	        			if (exifImageReference==exifImageArray[0]) {
	        				inImage = new File(filePair.getSecond().trim());
	        			}
	        			else {
	        				inImage = new File(filePair.getFirst().trim());
	        			}
	    				outFile = new File(outDirectory+outFileBase+"_NRG.jpg");
	    		    	exifWriter = new WriteEXIF(inImage, outFile, tempFile);
	    				exifWriter.copyEXIF();
	    			}
	    	
	    		}
	    		IJ.run("Close All");
	    		//bufWriter.close();
	    	}
	    	bufWriter.close();
	    } catch (Exception e) {
	    	IJ.error("Error writing log file", e.getMessage());
	    	return;
	    }
	}
	
	// Method to update dialog based on user selections
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		Checkbox clipImagesCheckbox = (Checkbox)gd.getCheckboxes().get(3);
		Checkbox IndexColorCheckbox = (Checkbox)gd.getCheckboxes().get(5);
		Checkbox IndexFloatCheckbox = (Checkbox)gd.getCheckboxes().get(6);
		Checkbox stretchVisCheckbox = (Checkbox)gd.getCheckboxes().get(7);
		Checkbox stretchNIRCheckbox = (Checkbox)gd.getCheckboxes().get(8);
		Vector<?> choices = gd.getChoices();
		Vector<?> numericChoices = gd.getNumericFields();
		Vector<?> checkboxChoices = gd.getCheckboxes();
		if (clipImagesCheckbox.getState()) {
			((Checkbox)checkboxChoices.get(4)).setEnabled(true);
		}
		else {
			((Checkbox)checkboxChoices.get(4)).setEnabled(false);
		}
		
		if (IndexColorCheckbox.getState() | IndexFloatCheckbox.getState()) {
			((Choice)choices.get(5)).setEnabled(true);
			((Choice)choices.get(6)).setEnabled(true);
			((Choice)choices.get(7)).setEnabled(true);
			((Checkbox)checkboxChoices.get(7)).setEnabled(true);
			((Checkbox)checkboxChoices.get(8)).setEnabled(true);
			((TextField)numericChoices.get(3)).setEnabled(true);
		} 
		else {
			((Choice)choices.get(5)).setEnabled(false);
			((Choice)choices.get(6)).setEnabled(false);
			((Choice)choices.get(7)).setEnabled(false);
			((Checkbox)checkboxChoices.get(7)).setEnabled(false);
			((Checkbox)checkboxChoices.get(8)).setEnabled(false);
			((TextField)numericChoices.get(3)).setEnabled(false);
		}
		Checkbox useSecondaryMethodsCheckbox = (Checkbox)gd.getCheckboxes().get(1);
		if (useSecondaryMethodsCheckbox.getState()) {
			((Choice)choices.get(1)).setEnabled(true);
		}
		else {
			((Choice)choices.get(1)).setEnabled(false);
		}
		
		int primaryChoice = gd.getNextChoiceIndex();
		int secondaryChoice = gd.getNextChoiceIndex();
		Vector<?> numTriesVector = gd.getNumericFields();
		if (primaryChoice==0 || primaryChoice==1 || (secondaryChoice==0 && useSecondaryMethodsCheckbox.getState()) || 
				(secondaryChoice == 1 && useSecondaryMethodsCheckbox.getState())) {
			((Choice)choices.get(2)).setEnabled(true);
			((Choice)choices.get(3)).setEnabled(true);
			((TextField)numTriesVector.get(0)).setEnabled(true);
		}
		else {
			((Choice)choices.get(2)).setEnabled(false);
			((Choice)choices.get(3)).setEnabled(false);
			((TextField)numTriesVector.get(0)).setEnabled(false);
		}
		
		if (IndexColorCheckbox.getState()) {
			((Choice)choices.get(7)).setEnabled(true);
			((TextField)numericChoices.get(3)).setEnabled(true);
		} 
		else {
			((Choice)choices.get(7)).setEnabled(false);
			((TextField)numericChoices.get(3)).setEnabled(false);
		}
		
		if (stretchVisCheckbox.getState() | stretchNIRCheckbox.getState()) {
			((TextField)numericChoices.get(3)).setEnabled(true);
		}
		else {
			((TextField)numericChoices.get(3)).setEnabled(false);
		}
			
		return true;
	}
	
	// Do image-to-image registration using SIFT/Landmark correspondences
	public RoiPair doSift(ImagePlus rawSourceImage, ImagePlus rawTargetImage, int numSiftTries, String transformation, String preprocessingMethod) {
		IJ.log("Geting SIFT correspondence points from "+rawTargetImage.getTitle()+" and "+rawSourceImage.getTitle());
		ImagePlus processSourceImage = null;
		ImagePlus processTargetImage = null;
		int trys = 1;
		boolean noPoints = true;
		
		// Do pre-processing if requested
		if (preprocessingMethod == "nir (g+b) vis (g-b)") {
			processSourceImage = channelMath((ColorProcessor)rawSourceImage.getProcessor(), 2, 3, ADD);
			processTargetImage = channelMath((ColorProcessor)rawTargetImage.getProcessor(), 2, 3, SUBTRACT);				
		} 
		else if (preprocessingMethod == "nir=green band vis=green band") {
			ByteProcessor bpSource =  new ByteProcessor(rawSourceImage.getWidth(), rawSourceImage.getHeight());
			ByteProcessor bpTarget =  new ByteProcessor(rawTargetImage.getWidth(), rawTargetImage.getHeight());
			ColorProcessor cpSource = (ColorProcessor)rawSourceImage.getProcessor();
			ColorProcessor cpTarget = (ColorProcessor)rawTargetImage.getProcessor();
			processSourceImage = new ImagePlus("Source", cpSource.getChannel(2, bpSource));
			processTargetImage = new ImagePlus("Target", cpTarget.getChannel(2, bpTarget));
		}
		else if (preprocessingMethod == "none") {
			processSourceImage = new ImagePlus("Source", rawSourceImage.getProcessor());
			processTargetImage = new ImagePlus("Target", rawTargetImage.getProcessor());
		}
		processSourceImage.show();
		processTargetImage.show();

		processSourceImage.setTitle("sourceProcessed");
		processTargetImage.setTitle("targetProcessed");

		// Try 5 times to get a set of match points for the transformation
		while (trys <= numSiftTries && noPoints) {
			IJ.log("Number of times trying SIFT: "+trys);
			// Get match points using SIFT
			IJ.run("Extract SIFT Correspondences", "source_image="+processTargetImage.getTitle()+" target_image="+
					processSourceImage.getTitle()+" initial_gaussian_blur=1.60    " +
							"steps_per_scale_octave=3 minimum_image_size=64 maximum_image_size=1024 " +
							"feature_descriptor_size=4 feature_descriptor_orientation_bins=8 closest/   " +
							"next_closest_ratio=0.92 filter maximal_alignment_error=25 " +
							"minimal_inlier_ratio=0.05 minimal_number_of_inliers=7 expected_transformation="+
							transformation);
			if (processSourceImage.getRoi() != null) {
				if (processTargetImage.getRoi().getType() == Roi.POINT) {
					noPoints = false;
				}
			}
			trys = trys + 1;
		}
		if (!noPoints) {
			
			RoiPair rois = new RoiPair(processSourceImage.getRoi(), processTargetImage.getRoi());
			processSourceImage.close();
			processTargetImage.close();
			return rois;
		}
		else {
			return null;
		}
	}
	
	// Method to run Landmark correspondence
	public ImagePlus doLandmark(ImagePlus rawSourceImage, ImagePlus rawTargetImage, String transformation) {
		IJ.log("Registering "+rawTargetImage.getTitle()+" and "+rawSourceImage.getTitle()+ " using Landmark correspondences");
		IJ.run("Landmark Correspondences", "source_image="+rawSourceImage.getTitle()+" template_image="+
				rawTargetImage.getTitle()+" transformation_method=[Moving Least Squares (non-linear)] " +
				"alpha=1 mesh_resolution=32 transformation_class="+transformation+
				" interpolate");
			ImagePlus regSource = WindowManager.getImage("Transformed"+rawSourceImage.getTitle());
			return regSource;
	}
	
	// Method to run bUnwarpJ
	public ImagePlus dobUnwarpJ(ImagePlus rawSourceImage, ImagePlus rawTargetImage, String transformation) {
		IJ.log("Processing "+rawSourceImage+" and "+rawTargetImage+" using bUnwarpJ");
		IJ.run("bUnwarpJ", "source_image="+rawSourceImage.getTitle()+" target_image="+rawTargetImage.getTitle()+
				" registration=Fast image_subsample_factor=0 initial_deformation=[Very Coarse] " +
				"inal_deformation=Fine divergence_weight=0 curl_weight=0 landmark_weight=0 image_weight=1 " +
				"consistency_weight=10 stop_threcreateFloatImage(shold=0.01");
		ImagePlus regSource = new ImagePlus("newSourceImage", WindowManager.getImage("Registered Source Image").getStack().getProcessor(1));
		return regSource;
	}
	
	
 
	// Method to perform channel math
	public ImagePlus channelMath (ColorProcessor image, int chanA, int chanB, int operator) {
		int width = image.getWidth();
		int height = image.getHeight();
		ImagePlus newImage;
		newImage = NewImage.createFloatImage("resultImage", width, height, 1, NewImage.FILL_BLACK);
		byte[] array1 = image.getChannel(chanA);
		byte[] array2 = image.getChannel(chanB);
		double outPixel = 0.0;

		for (int y=0; y<height; y++) {
            int offset = y*width;
			for (int x=0; x<width; x++) {
				int pos = offset+x;
				double pixel1 = array1[pos] & 0xff;
				double pixel2 = array2[pos] & 0xff;
                switch (operator) {
                	case SUBTRACT: outPixel = pixel1 - pixel2; break;
                	case ADD: outPixel = pixel1 + pixel2; break;
                	case MULTIPLY: outPixel = pixel1 * pixel2; break;
                	case DIVIDE: outPixel = pixel2!=0.0?pixel1/pixel2:0.0; break;
                }
                	newImage.getProcessor().putPixelValue(x, y, outPixel);
            }
		}
		return newImage;
	}
	
	//Method to clip image pair
	public RegImagePair clipPair (ImagePlus clipSourceImage, ImagePlus otherImage) {
		if ((clipSourceImage.getHeight() != otherImage.getHeight()) | (clipSourceImage.getWidth() != otherImage.getWidth())) {
			return null;
		}
		ImageProcessor byteImage = clipSourceImage.getProcessor().convertToByte(true);
		ImagePlus croppedSource = null;
		ImagePlus croppedOther = null;
		int height = clipSourceImage.getHeight();
		int width = clipSourceImage.getWidth();
		int xmin = 0;
		int ymin = 0;
		int xmax = width - 1;
		int ymax = height - 1;
		boolean topHasNoData = true;
		boolean rightHasNoData = true;
		boolean bottomHasNoData = true;
		boolean leftHasNoData = true;
	    int sidesOK = 0;
		
	    while (sidesOK < 4) {
	    	double proportionNoData = 0.0;
	    	String moveSide = "";
	    	int numNoData = 0;
	    	
	    	// For each side count the number of no-data pixel in the line or column then calculate percent no-data
	         if (topHasNoData) {
	            numNoData = 0;
	            for (int x=xmin; x<=xmax; x++) {
	               if (byteImage.getPixel(x, ymin) == 0) {
	                   numNoData++;
	               }
	            }
	            if ((numNoData/(double)(xmax+1.0)) > proportionNoData) {
	               proportionNoData = numNoData/(double)(xmax+1.0);
	               moveSide = "top";
	            } else if (numNoData == 0) {
	              topHasNoData = false;
	              sidesOK = sidesOK + 1;
	            }
	         }
	         if (rightHasNoData) {
	            numNoData = 0;
	           for (int y=ymin; y<=ymax; y++) {
	               if (byteImage.getPixel(xmax, y) == 0) {
	                   numNoData++;
	               }
	            }
	            if ((numNoData/(double)(ymax+1.0)) > proportionNoData) {
	               proportionNoData = numNoData/(double)(ymax+1.0);
	               moveSide = "right";
	            } else if (numNoData == 0) {
	              rightHasNoData = false;
	              sidesOK = sidesOK + 1;
	            }
	         }
	         if (bottomHasNoData) {
	            numNoData = 0;
	            for (int x=xmin; x<=xmax; x++) {
	               if (byteImage.getPixel(x, ymax) == 0) {
	                   numNoData++;
	               }
	            }
	            if ((numNoData/(double)(xmax+1.0)) > proportionNoData) {
	               proportionNoData = numNoData/(double)(xmax+1.0);
	               moveSide = "bottom";
	            } else if (numNoData == 0) {
	              bottomHasNoData = false;
	              sidesOK = sidesOK + 1;
	            }
	         }
	         if (leftHasNoData) {
	            numNoData = 0;
	            for (int y=ymin; y<=ymax; y++) {
	               if (byteImage.getPixel(xmin, y) == 0) {
	                   numNoData++;
	               }
	            }
	            if ((numNoData/(double)(ymax+1.0)) > proportionNoData) {
	               proportionNoData = numNoData/(double)(ymax+1.0);
	               moveSide = "left";
	            } else if (numNoData == 0) {
	              rightHasNoData = false;
	              sidesOK = sidesOK + 1;
	            }
	         }
	         // Move the side that has the highest proportion of no-data pixels
	         if (moveSide == "top") {
	            ymin = ymin + 1;
	         }
	         if (moveSide == "right") {
	            xmax = xmax - 1;
	         }
	         if (moveSide == "bottom") {
	            ymax = ymax - 1;
	         }
	         if (moveSide == "left") {
	            xmin = xmin + 1;
	         }
	    }
	    // Calculate selection rectangle
	    clipSourceImage.setRoi(xmin,ymin, xmax - xmin + 1, ymax - ymin + 1);
	    otherImage.setRoi(xmin,ymin, xmax - xmin + 1, ymax - ymin + 1);
	    clipSourceImage.show();
	    otherImage.show();
	    croppedSource = new ImagePlus("croppedFirst", clipSourceImage.getProcessor().crop());
	    croppedOther = new ImagePlus("croppedSecond", otherImage.getProcessor().crop());
	    RegImagePair outPair = new RegImagePair(croppedSource, croppedOther);
	    return outPair;
	}
}