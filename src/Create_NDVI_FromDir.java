import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.plugin.*;
import ij.process.LUT;
import ij.Prefs;
import ij.gui.DialogListener;
import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.TextField;
import java.awt.image.IndexColorModel;
import java.io.*;
import java.util.Vector;

public class Create_NDVI_FromDir implements PlugIn, DialogListener {
	public void run(String arg) {
		String[] indexTypes = {"NDVI: (NIR-Vis)/(NIR+Vis)", "DVI: NIR-Vis"};
		//String[] outputImageTypes = {"tiff", "jpeg", "gif", "zip", "raw", "avi", "bmp", "fits", "png", "pgm"};
		String[] IndexBands = {"red", "green", "blue"};	
		// Get list of LUTs
		String lutLocation = IJ.getDirectory("luts");
		File lutDirectory = new File(lutLocation);
		String[] lutNames = lutDirectory.list();
		String logName = "log.txt";
		File outFile = null;
		File tempFile = null;
		ImagePlus inImagePlus = null;
		ImagePlus indexImage = null;
		String outFileBase = "";
		int redBand, irBand;
		Boolean saveParameters = true;
		Boolean useDefaults = false;
		
		// Initialize variables from IJ.Prefs file
		String indexType = Prefs.get("pm.fromSBImage.indexType", indexTypes[0]);
		//String fileType = Prefs.get("pm.fromSBDir.fromSBDir.fileType", outputImageTypes[0]);
		Boolean createIndexColor = Prefs.get("pm.fromSBDir.createIndexColor", true);
		Boolean createIndexFloat = Prefs.get("pm.fromSBDir.createIndexFloat", true);
		Boolean stretchVisible = Prefs.get("pm.fromSBDir.stretchVisible", true);
		Boolean stretchIR = Prefs.get("pm.fromSBDir.stretchIR", true);
		double saturatedPixels = Prefs.get("pm.fromSBDir.saturatedPixels", 2.0);
		double maxColorScale = Prefs.get("pm.fromSBDir.maxColorScale", 1.0);
		double minColorScale = Prefs.get("pm.fromSBDir.minColorScale", -1.0);
		String lutName = Prefs.get("pm.fromSBDir.lutName", lutNames[0]);
		int redBandIndex = (int)Prefs.get("pm.fromSBDir.redBandIndex", 2); 
		int irBandIndex = (int)Prefs.get("pm.fromSBDir.irBandIndex", 0);
		saturatedPixels = Prefs.get("pm.fromSBDir.saturatedPixels", 2.0);
		
		// Create dialog window
		GenericDialog dialog = new GenericDialog("Enter variables");
		dialog.addCheckbox("Load default parameters (click OK below to reload)", false);
		dialog.addChoice("Select index type for calculation", indexTypes, indexType);
		dialog.addMessage("Output image options:");
		//dialog.addChoice("Output image type", outputImageTypes, fileType);
		dialog.addCheckbox("Output Color Index image?", createIndexColor);
		dialog.addNumericField("Minimum Index value for scaling color Index image", minColorScale, 2);
		dialog.addNumericField("Maximum Index value for scaling color Index image", maxColorScale, 2);
		dialog.addCheckbox("Output floating point Index image?", createIndexFloat);
		dialog.addCheckbox("Stretch the visible band before creating Index?", stretchVisible);
		dialog.addCheckbox("Stretch the NIR band before creating Index?", stretchIR);
		dialog.addNumericField("Saturation value for stretch", saturatedPixels, 1);
		dialog.addChoice("Channel for Red band to create Index", IndexBands, IndexBands[redBandIndex]);
		dialog.addChoice("Channel for IR band to create Index", IndexBands, IndexBands[irBandIndex]);
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
			dialog.addChoice("Select index type for calculation", indexTypes, indexTypes[0]);
			dialog.addMessage("Output image options:");
			//dialog.addChoice("Output image type", outputImageTypes, outputImageTypes[0]);
			dialog.addCheckbox("Output Color Index image?", true);
			dialog.addNumericField("Enter the minimum Index value for scaling color Index image", -1.0, 2);
			dialog.addNumericField("Enter the maximum Index value for scaling color Index image", 1.0, 2);
			dialog.addCheckbox("Output floating point Index image?", true);
			dialog.addCheckbox("Stretch the visible band before creating Index?", true);
			dialog.addCheckbox("Stretch the NIR band before creating Index?", true);
			dialog.addNumericField("Enter the saturation value for stretch", 2.0, 1);
			dialog.addChoice("Channel for Red band to create Index", IndexBands, IndexBands[2]);
			dialog.addChoice("Channel for IR band to create Index", IndexBands, IndexBands[0]);
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
		indexType = dialog.getNextChoice();
		//fileType = dialog.getNextChoice();
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
	
		if (saveParameters) {
			// Set preferences to IJ.Prefs file
			Prefs.set("pm.fromSBImage.indexType", indexType);
			//Prefs.set("pm.fromSBDir.fileType", fileType);
			Prefs.set("pm.fromSBDir.createIndexColor", createIndexColor);
			Prefs.set("pm.fromSBDir.createIndexFloat", createIndexFloat);
			Prefs.set("pm.fromSBDir.stretchVisible", stretchVisible);
			Prefs.set("pm.fromSBDir.stretchIR", stretchIR);
			Prefs.set("pm.fromSBDir.saturatedPixels", saturatedPixels);
			Prefs.set("pm.fromSBDir.maxColorScale", maxColorScale);
			Prefs.set("pm.fromSBDir.minColorScale", minColorScale);
			Prefs.set("pm.fromSBDir.lutName", lutName);
			Prefs.set("pm.fromSBDir.redBandIndex", redBand - 1);
			Prefs.set("pm.fromSBDir.irBandIndex", irBand - 1);
		
			// Save preferences to IJ.Prefs file
			Prefs.savePreferences();
		}
		
		// Dialog for input photo directory
	    DirectoryChooser inDirChoose = new DirectoryChooser("Input image directory");
        String inDir = inDirChoose.getDirectory();
        if (inDir == null) {
       	 IJ.error("Input image directory was not selected");
       	 return;
        }
        File inFolder = new File(inDir);
        File[] inputImages = inFolder.listFiles();
        
     // Dialog for output photos directory and log file name
     	SaveDialog sd = new SaveDialog("Output directory and log file name", "log", ".txt");
     	String outDirectory = sd.getDirectory();
     	logName = sd.getFileName();
     	if (logName==null){
     	   IJ.error("No directory was selected");
     	   return;
     	}
     	
     	try {
	    	BufferedWriter bufWriter = new BufferedWriter(new FileWriter(outDirectory+logName));
	    	// Write parameter settings to log file
	    	bufWriter.write("PARAMETER SETTINGS:\n");
	    	bufWriter.write("Select index type for calculation: " + indexType + "\n\n");
		    //bufWriter.write("Output image type: " + fileType + "\n");
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
	    	bufWriter.close();
	    } catch (Exception e) {
	    	IJ.error("Error writing log file", e.getMessage());
	    	return;
	    }

	    // Start processing one image at a time
	    for (File inImage : inputImages) {
	    	// Open image
	    	inImagePlus = new ImagePlus(inImage.getAbsolutePath());
	    	outFileBase = inImagePlus.getTitle().replaceFirst("[.][^.]+$", "");
	    	
	    	// Make sure images are RGB
	    	if (inImagePlus.getType() != ImagePlus.COLOR_RGB) {
	    		IJ.error("Images must be Color RGB");
	    		return;  
	    	}
	    	
	    	inImagePlus.show();
	    	RegImagePair imagePair = new RegImagePair(inImagePlus, inImagePlus);
	    	indexImage = imagePair.calcNDVI(irBand, redBand, stretchVisible, stretchIR, saturatedPixels);
	    	if (indexType == indexTypes[0]) {
	    		indexImage = imagePair.calcNDVI(irBand, redBand, stretchVisible, stretchIR, saturatedPixels);
	    	} 
	    	else if (indexType == indexTypes[1]) {
	    		indexImage = imagePair.calcDVI(irBand, redBand, stretchVisible, stretchIR, saturatedPixels);
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
    			colorIndex.show();
				String tempFileName = outDirectory+outFileBase+"IndexColorTemp.jpg";
				tempFile = new File(tempFileName);
				IJ.save(colorIndex, tempFileName);
    			if (indexType == indexTypes[0]) {
    				//IJ.save(colorIndex, outDirectory+outFileBase+"_NDVI_Color."+fileType);
    				outFile = new File(outDirectory+outFileBase+"_NDVI_Color.jpg");
    			}
    			else if (indexType == indexTypes[1]) {
    				//IJ.save(colorIndex, outDirectory+outFileBase+"_DVI_Color."+fileType);
    				outFile = new File(outDirectory+outFileBase+"_DVI_Color.jpg");
    			}
    		}
	    	IJ.run("Close All");
	    	WriteEXIF exifWriter = new WriteEXIF(inImage, outFile, tempFile);
			exifWriter.copyEXIF();
	    }
	}
	
	// Method to update dialog based on user selections
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
			Checkbox IndexColorCheckbox = (Checkbox)gd.getCheckboxes().get(1);
			Vector<?> numericChoices = gd.getNumericFields();
			Vector<?> choices = gd.getChoices();
			if (IndexColorCheckbox.getState()) {
				((TextField)numericChoices.get(0)).setEnabled(true);
				((TextField)numericChoices.get(1)).setEnabled(true);
				((Choice)choices.get(3)).setEnabled(true);
			} 
			else {
				((TextField)numericChoices.get(0)).setEnabled(false);
				((TextField)numericChoices.get(1)).setEnabled(false);
				((Choice)choices.get(3)).setEnabled(false);
			}			
			return true;
		}
}


