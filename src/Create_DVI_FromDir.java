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

public class Create_DVI_FromDir implements PlugIn, DialogListener {
	public void run(String arg) {
		String[] outputImageTypes = {"tiff", "jpeg", "gif", "zip", "raw", "avi", "bmp", "fits", "png", "pgm"};
		String[] DVIBands = {"red", "green", "blue"};	
		// Get list of LUTs
		String lutLocation = IJ.getDirectory("luts");
		File lutDirectory = new File(lutLocation);
		String[] lutNames = lutDirectory.list();
		String logName = "log.txt";
		
		ImagePlus inImagePlus = null;
		ImagePlus DVIImage = null;
		String outFileBase = "";
		int redBand, irBand;
		Boolean saveParameters = true;
		Boolean useDefaults = false;
		
		// Initialize variables from IJ.Prefs file
		String fileType = Prefs.get("pm.fromSBDir.fromSBDir.fileType", outputImageTypes[0]);
		Boolean createDVIColor = Prefs.get("pm.fromSBDir.createDVIColor", true);
		Boolean createDVIFloat = Prefs.get("pm.fromSBDir.createDVIFloat", true);
		Boolean stretchVisible = Prefs.get("pm.fromSBDir.stretchVisible", true);
		Boolean stretchIR = Prefs.get("pm.fromSBDir.stretchIR", true);
		double saturatedPixels = Prefs.get("pm.fromSBDir.saturatedPixels", 2.0);
		double maxColorScale = Prefs.get("pm.fromSBDir.maxColorScale", 255.0);
		double minColorScale = Prefs.get("pm.fromSBDir.minColorScale", 0.0);
		String lutName = Prefs.get("pm.fromSBDir.lutName", lutNames[0]);
		int redBandIndex = (int)Prefs.get("pm.fromSBDir.redBandIndex", 2); 
		int irBandIndex = (int)Prefs.get("pm.fromSBDir.irBandIndex", 0);
		saturatedPixels = Prefs.get("pm.fromSBDir.saturatedPixels", 2.0);
		
		// Create dialog window
		GenericDialog dialog = new GenericDialog("Enter variables");
		dialog.addCheckbox("Load default parameters (click OK below to reload)", false);
		dialog.addMessage("Output image options:");
		dialog.addChoice("Output image type", outputImageTypes, fileType);
		dialog.addCheckbox("Output Color DVI image?", createDVIColor);
		dialog.addNumericField("Minimum DVI value for scaling color DVI image", minColorScale, 1);
		dialog.addNumericField("Maximum DVI value for scaling color DVI image", maxColorScale, 1);
		dialog.addCheckbox("Output floating point DVI image?", createDVIFloat);
		dialog.addCheckbox("Stretch the visible band before creating DVI?", stretchVisible);
		dialog.addCheckbox("Stretch the NIR band before creating DVI?", stretchIR);
		dialog.addNumericField("Saturation value for stretch", saturatedPixels, 1);
		dialog.addChoice("Channel for Red band to create DVI", DVIBands, DVIBands[redBandIndex]);
		dialog.addChoice("Channel for IR band to create DVI", DVIBands, DVIBands[irBandIndex]);
		dialog.addChoice("Select output color table for color DVI image", lutNames, lutName);
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
			dialog.addMessage("Output image options:");
			dialog.addChoice("Output image type", outputImageTypes, outputImageTypes[0]);
			dialog.addCheckbox("Output Color DVI image?", true);
			dialog.addNumericField("Enter the minimum DVI value for scaling color DVI image", -1.0, 1);
			dialog.addNumericField("Enter the maximum DVI value for scaling color DVI image", 1.0, 1);
			dialog.addCheckbox("Output floating point DVI image?", true);
			dialog.addCheckbox("Stretch the visible band before creating DVI?", true);
			dialog.addCheckbox("Stretch the NIR band before creating DVI?", true);
			dialog.addNumericField("Enter the saturation value for stretch", 2.0, 1);
			dialog.addChoice("Channel for Red band to create DVI", DVIBands, DVIBands[2]);
			dialog.addChoice("Channel for IR band to create DVI", DVIBands, DVIBands[0]);
			dialog.addChoice("Select output color table for color DVI image", lutNames, lutNames[0]);
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
		fileType = dialog.getNextChoice();
		createDVIColor = dialog.getNextBoolean();
		minColorScale = dialog.getNextNumber();
		maxColorScale = dialog.getNextNumber();
		createDVIFloat = dialog.getNextBoolean();
		stretchVisible = dialog.getNextBoolean();
		stretchIR = dialog.getNextBoolean();
		saturatedPixels = dialog.getNextNumber();
		redBand = dialog.getNextChoiceIndex() + 1;
		irBand = dialog.getNextChoiceIndex() + 1;
		lutName  = dialog.getNextChoice();
		saveParameters  = dialog.getNextBoolean();
	
		if (saveParameters) {
			// Set preferences to IJ.Prefs file
			Prefs.set("pm.fromSBDir.fileType", fileType);
			Prefs.set("pm.fromSBDir.createDVIColor", createDVIColor);
			Prefs.set("pm.fromSBDir.createDVIFloat", createDVIFloat);
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
		    bufWriter.write("Output image type: " + fileType + "\n");
		    bufWriter.write("Output Color DVI image? " + createDVIColor + "\n");
		    bufWriter.write("Minimum DVI value for scaling color DVI image: " + minColorScale + "\n");
		    bufWriter.write("Maximum DVI value for scaling color DVI image: " + maxColorScale + "\n");
		    bufWriter.write("Output floating point DVI image? " + createDVIFloat + "\n");
		    bufWriter.write("Stretch the visible band before creating DVI? " + stretchVisible + "\n");
		    bufWriter.write("Stretch the NIR band before creating DVI? " + stretchIR + "\n");
		    bufWriter.write("Saturation value for stretch: " + saturatedPixels + "\n");
		    bufWriter.write("Channel from visible image to use for Red band to create DVI: " + redBand + "\n");
		    bufWriter.write("Channel from IR image to use for IR band to create DVI: " + irBand + "\n");
		    bufWriter.write("Select output color table for color DVI image: " + lutName + "\n\n");
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
	    	DVIImage = imagePair.calcDVI(irBand, redBand, stretchVisible, stretchIR, saturatedPixels);
	    		
	    	if (createDVIFloat) {
    			IJ.save(DVIImage, outDirectory+outFileBase+"_DVI_Float."+fileType);
    		}
	    		
	    	if (createDVIColor) {
    			IndexColorModel cm = null;
    			LUT lut;
    			// Uncomment next line to use default float-to-byte conversion
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
    			IJ.save(colorDVI, outDirectory+outFileBase+"_DVI_Color."+fileType);
    		}
	    		IJ.run("Close All");
	    }
	}
	
	// Method to update dialog based on user selections
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
			Checkbox DVIColorCheckbox = (Checkbox)gd.getCheckboxes().get(1);
			Vector<?> numericChoices = gd.getNumericFields();
			Vector<?> choices = gd.getChoices();
			if (DVIColorCheckbox.getState()) {
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


