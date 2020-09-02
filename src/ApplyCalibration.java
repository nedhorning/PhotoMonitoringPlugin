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

public class ApplyCalibration implements PlugIn, DialogListener {
	public void run(String arg) {
		String[] indexTypes = {"NDVI (NIR-Vis)/(NIR+Vis)", "DVI NIR-Vis"};
		//String[] IndexBands = {"red", "green", "blue"};	
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
		int visBand = 0;
		int nirBand = 0;
		ImagePlus colorIndex = null;
		// Calibration coefficients stored as visible intercept, slope, NIR intercept, slope
		double[] calibrationCoefs = new double[4];
		Boolean subtractNIR = null;
		double percentToSubtract = 0.0;
		Boolean removeGamma = null;
		double gamma = 0.0;
		Boolean saveParameters = true;
		Boolean useDefaults = false;
		
		// Initialize variables from IJ.Prefs file
		String indexType = Prefs.get("pm.fromSBImage.indexType", indexTypes[0]);
		Boolean createIndexColor = Prefs.get("pm.ac.createIndexColor", true);
		Boolean createIndexFloat = Prefs.get("pm.ac.createIndexFloat", true);
		double maxColorScale = Prefs.get("pm.ac.maxColorScale", 1.0);
		double minColorScale = Prefs.get("pm.ac.minColorScale", -1.0);
		String lutName = Prefs.get("pm.ac.lutName", lutNames[0]);
		//int visBandIndex = (int)Prefs.get("pm.ac.visibleBandIndex", 0); 
		//int nirBandIndex = (int)Prefs.get("pm.ac.nirBandIndex", 2);
		
		// Create dialog window
		GenericDialog dialog = new GenericDialog("Enter variables");
		dialog.addCheckbox("Load default parameters (click OK below to reload)", false);
		dialog.addChoice("Select index type for calculation", indexTypes, indexType);
		dialog.addMessage("Output image options:");
		dialog.addCheckbox("Output Color Index image?", createIndexColor);
		dialog.addNumericField("Minimum Index value for scaling color Index image", minColorScale, 1);
		dialog.addNumericField("Maximum Index value for scaling color Index image", maxColorScale, 1);
		dialog.addCheckbox("Output floating point Index image?", createIndexFloat);
		//dialog.addChoice("Channel for visible band to create Index", IndexBands, IndexBands[visBandIndex]);
		//dialog.addChoice("Channel for IR band to create Index", IndexBands, IndexBands[nirBandIndex]);
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
			dialog.addCheckbox("Output Color Index image?", true);
			dialog.addNumericField("Enter the minimum Index value for scaling color Index image", -1.0, 1);
			dialog.addNumericField("Enter the maximum Index value for scaling color Index image", 1.0, 1);
			dialog.addCheckbox("Output floating point Index image?", true);
			//dialog.addChoice("Channel for visible band to create Index", IndexBands, IndexBands[2]);
			//dialog.addChoice("Channel for IR band to create Index", IndexBands, IndexBands[0]);
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
		createIndexColor = dialog.getNextBoolean();
		minColorScale = dialog.getNextNumber();
		maxColorScale = dialog.getNextNumber();
		createIndexFloat = dialog.getNextBoolean();
		//visBand = dialog.getNextChoiceIndex();
		//nirBand = dialog.getNextChoiceIndex();
		lutName  = dialog.getNextChoice();
		saveParameters  = dialog.getNextBoolean();
	
		if (saveParameters) {
			// Set preferences to IJ.Prefs file
			Prefs.set("pm.ac.indexType", indexType);
			Prefs.set("pm.ac.createIndexColor", createIndexColor);
			Prefs.set("pm.ac.createIndexFloat", createIndexFloat);
			Prefs.set("pm.ac.maxColorScale", maxColorScale);
			Prefs.set("pm.ac.minColorScale", minColorScale);
			Prefs.set("pm.ac.lutName", lutName);
			//Prefs.set("pm.ac.visibleBandIndex", visBand);
			//Prefs.set("pm.ac.nirBandIndex", nirBand);
		
			// Save preferences to IJ.Prefs file
			Prefs.savePreferences();
		}
		
		// Dialog for selecting calibration file
		OpenDialog od = new OpenDialog("Select calibration file", arg);
	    String calibrationDirectory = od.getDirectory();
	    String calibrationFileName = od.getFileName();
	    if (calibrationFileName==null) {
	    	IJ.error("No file was selected");
	    	return;
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
     	
	    // Open calibration file, read each line and fill vis and nir coefficient arrays
        BufferedReader fileReader = null;
        try
        {
            String fullLine = "";
            fileReader = new BufferedReader(new FileReader(calibrationDirectory+calibrationFileName));	            
        
			int counter = 1;
            while ((fullLine = fileReader.readLine()) != null)
            {
                // Parse specific lines to get regression coefficients
            	if (counter == 8) {
                    String[] dataValues = fullLine.split(":");
                    calibrationCoefs[0] = (Double.parseDouble(dataValues[1]));
                }
                if (counter == 9) {
                    String[] dataValues = fullLine.split(":");
                    calibrationCoefs[1] = (Double.parseDouble(dataValues[1]));
                }
                if (counter == 11) {
                    String[] dataValues = fullLine.split(":");
                    calibrationCoefs[2] = (Double.parseDouble(dataValues[1]));
                }
                if (counter == 12) {
                    String[] dataValues = fullLine.split(":");
                    calibrationCoefs[3] = (Double.parseDouble(dataValues[1]));
                }               
                if (counter == 14) {
                    String[] dataValues = fullLine.split(":");
                    subtractNIR = (Boolean.parseBoolean(dataValues[1]));
                }
                if (counter == 15) {
                    String[] dataValues = fullLine.split(":");
                    percentToSubtract = (Double.parseDouble(dataValues[1]));
                }
                if (counter == 16) {
                    String[] dataValues = fullLine.split(":");
                    removeGamma = (Boolean.parseBoolean(dataValues[1]));
                }
                if (counter == 17) {
                    String[] dataValues = fullLine.split(":");
                    gamma = (Double.parseDouble(dataValues[1]));
                }
                if (counter == 19) {
                    String[] dataValues = fullLine.split(":");
                    visBand = (Integer.parseInt(dataValues[1].trim()))-1;
                }
                if (counter == 20) {
                    String[] dataValues = fullLine.split(":");
                    nirBand = (Integer.parseInt(dataValues[1].trim()))-1;
                }
                counter++;
            }
        }
        catch (Exception e) {
        	IJ.error("Error reading calibration coefficient file", e.getMessage());
        	return;
        }
        finally
        {
            try {
                fileReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
     	
     	try {
	    	BufferedWriter bufWriter = new BufferedWriter(new FileWriter(outDirectory+logName));
	    	// Write parameter settings to log file
	    	bufWriter.write("PARAMETER SETTINGS:\n");
	    	bufWriter.write("File name for calibration coeficients: "+ calibrationFileName + "\n");
	    	bufWriter.write("Select index type for calculation: " + indexType + "\n\n");
		    bufWriter.write("Output Color Index image? " + createIndexColor + "\n");
		    bufWriter.write("Minimum Index value for scaling color Index image: " + minColorScale + "\n");
		    bufWriter.write("Maximum Index value for scaling color Index image: " + maxColorScale + "\n");
		    bufWriter.write("Output floating point Index image? " + createIndexFloat + "\n");
		    bufWriter.write("Channel from visible image to use for Red band to create Index: " + (visBand+1) + "\n");
		    bufWriter.write("Channel from IR image to use for IR band to create Index: " + (nirBand+1) + "\n");
		    bufWriter.write("Subtract NIR from visible?"+subtractNIR + "\n");
		    bufWriter.write("Percent of NIR to subtract: "+percentToSubtract + "\n");
		    bufWriter.write("Remove gamma effect? "+removeGamma + "\n");
		    bufWriter.write("Gammafactor: "+gamma + "\n");
		    bufWriter.write("Visible band: " + (visBand+1) + "\n");
		    bufWriter.write("Near-infrared band: " + (nirBand+1) + "\n");
		    bufWriter.write("Select output color table for color Index image: " + lutName + "\n\n");
	    	bufWriter.close();
	    } catch (Exception e) {
	    	IJ.error("Error writing log file", e.getMessage());
	    	return;
	    }
     	
     	percentToSubtract = percentToSubtract / 100.0;

	    // Start processing one image at a time
	    for (File inImage : inputImages) {
	    	// Open image
	    	inImagePlus = new ImagePlus(inImage.getAbsolutePath());
	    	// Test to see if the file is an image
	    	if (inImagePlus.getImage() != null) {
	    		outFileBase = inImagePlus.getTitle().replaceFirst("[.][^.]+$", "");
	    		inImagePlus.show();
	    		
				double visPixel = 0.0;
				double nirPixel = 0.0;
				
/*				int maxScaleValue = 0;
				if(inImagePlus.getType() == ImagePlus.COLOR_RGB) {
					maxScaleValue = 255;
				} else {
					maxScaleValue = 65535;
				}*/
				
				if (inImagePlus.getNChannels() == 1) {
					//IJ.run("Make Composite");
					//IJ.run("16-bit");
					//inImagePlus = IJ.getImage();
					inImagePlus = new CompositeImage(inImagePlus);
				}
				
				// Split image into individual bands
				ImagePlus[] imageBands = ChannelSplitter.split(inImagePlus);
				//ImagePlus visImage = imageBands[visBandIndex];
				//ImagePlus nirImage = imageBands[nirBandIndex];		
				ImagePlus visImage = scaleImage(imageBands[visBand], "visImage");
				ImagePlus nirImage = scaleImage(imageBands[nirBand], "nirImage");

				
				if(removeGamma){
					//double undoGamma = 1/gamma;
					for (int y=0; y<nirImage.getHeight(); y++) {
						for (int x=0; x<nirImage.getWidth(); x++) {					
							nirPixel = Math.pow((nirImage.getProcessor().getPixelValue(x, y)), gamma);
							visPixel = Math.pow((visImage.getProcessor().getPixelValue(x, y)), gamma);
							visImage.getProcessor().putPixelValue(x, y, visPixel);
							nirImage.getProcessor().putPixelValue(x, y, nirPixel);
						}
					}
				}
				
				if(subtractNIR){
					// Subtract some of the NIR from visible band
					for (int y=0; y<nirImage.getHeight(); y++) {
						for (int x=0; x<nirImage.getWidth(); x++) {					
							nirPixel = nirImage.getProcessor().getPixelValue(x, y);
							visPixel = visImage.getProcessor().getPixelValue(x, y) - (percentToSubtract * nirPixel);
							visImage.getProcessor().putPixelValue(x, y, visPixel);
						}
					}
				}
				//visImage.show();
				//nirImage.show();

	    		if (indexType == indexTypes[0]) {
	    			indexImage = makeNDVI(visImage, nirImage, calibrationCoefs);
	    			indexImage.show();
	    		} 
	    		else if (indexType == indexTypes[1]) {
	    			indexImage = makeDVI(visImage, nirImage, calibrationCoefs);
	    		}
	    		
	    		if (createIndexFloat) {
	    			if (indexType == indexTypes[0]) {
	    				IJ.save(indexImage, outDirectory+outFileBase+"_NDVI_Float."+"tif");
	    				//outFile = new File(outDirectory+outFileBase+"_NDVI_Float.tif");
	    			}
	    			else if (indexType == indexTypes[1]) {
	    				IJ.save(indexImage, outDirectory+outFileBase+"_DVI_Float."+"tif");
	    			}
	    			//tempFile = null;
	    			//WriteEXIF exifWriter = new WriteEXIF(inImage, outFile, tempFile);
					//exifWriter.copyExifFromJpeg2Tiff(indexImage);
    			}
	    		
	    		if (createIndexColor) {
    				IndexColorModel cm = null;
    				LUT lut;
    				// Uncomment next line to use default float-to-byte conversion
    				//ImageProcessor colorNDVI = ndviImage.getProcessor().convertToByte(true);
    				colorIndex = null;
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
    				String tempFileName = outDirectory+outFileBase+"IndexColorTemp."+"jpg";
    				tempFile = new File(tempFileName);
    				IJ.save(colorIndex, tempFileName);
    				if (indexType == indexTypes[0]) {
    					//IJ.save(colorIndex, outDirectory+outFileBase+"_NDVI_Color."+"jpg");
    					outFile = new File(outDirectory+outFileBase+"_NDVI_Color."+"jpg");
    				}
    				else if (indexType == indexTypes[1]) {
    					//IJ.save(colorIndex, outDirectory+outFileBase+"_DVI_Color."+"jpg");
    					outFile = new File(outDirectory+outFileBase+"_DVI_Color."+"jpg");
    				}
    			}
		    	IJ.run("Close All");
		    	WriteEXIF exifWriter = new WriteEXIF(inImage, outFile, tempFile);
				exifWriter.copyEXIF();
	    	}	
	    }
	}
	
	// Method to create NDVI image
	public ImagePlus makeNDVI(ImagePlus visImage, ImagePlus nirImage, double[] calibrationCeofs) {
			double nirPixel, visPixel, outPixel = 0.0;
			ImagePlus newImage;
			newImage = NewImage.createFloatImage("ndviImage", nirImage.getWidth(), nirImage.getHeight(), 1, NewImage.FILL_BLACK);
			for (int y=0; y<nirImage.getHeight(); y++) {
				for (int x=0; x<nirImage.getWidth(); x++) {
					nirPixel = nirImage.getProcessor().getPixelValue(x, y) * calibrationCeofs[3] + calibrationCeofs[2];
					visPixel = (visImage.getProcessor().getPixelValue(x, y) * calibrationCeofs[1] + calibrationCeofs[0]);
					if ((nirPixel + visPixel) == 0.0) {
						outPixel = 0.0;
					} else {					
						outPixel = (nirPixel - visPixel)/(nirPixel + visPixel);
						if (outPixel > 1.0) outPixel = 1.0;
						if (outPixel < -1.0) outPixel = -1.0;
					}
					newImage.getProcessor().putPixelValue(x, y, outPixel);
				}
			}
			return newImage;
	}
	
	// Method to create DVI image
	public ImagePlus makeDVI(ImagePlus visImage, ImagePlus nirImage, double[] calibrationCeofs) {
			double nirPixel, visPixel, outPixel = 0.0;
			ImagePlus newImage;
			newImage = NewImage.createFloatImage("ndviImage", nirImage.getWidth(), nirImage.getHeight(), 1, NewImage.FILL_BLACK);
			for (int y=0; y<nirImage.getHeight(); y++) {
				for (int x=0; x<nirImage.getWidth(); x++) {
					nirPixel = nirImage.getProcessor().getPixelValue(x, y) * calibrationCeofs[3] + calibrationCeofs[2];
					visPixel = (visImage.getProcessor().getPixelValue(x, y) * calibrationCeofs[1] + calibrationCeofs[0]);					
					outPixel = nirPixel - visPixel;
					newImage.getProcessor().putPixelValue(x, y, outPixel);
				}
			}
			newImage.show();
			return newImage;
	}
	
	// Method to scale bands to 0 - 1
	public ImagePlus scaleImage(ImagePlus inImage, String imageName) {
			double inPixel=0.0, outPixel = 0.0;
			ImagePlus newImage;
			double minVal = inImage.getProcessor().getMin();
			double maxVal = inImage.getProcessor().getMax();
			double inverseRange = 1 / (maxVal - minVal);
			newImage = NewImage.createFloatImage(imageName, inImage.getWidth(), inImage.getHeight(), 1, NewImage.FILL_BLACK);
			for (int y=0; y<inImage.getHeight(); y++) {
				for (int x=0; x<inImage.getWidth(); x++) {
					inPixel = inImage.getPixel(x, y)[0];					
					//outPixel = inPixel / maxScaleValue;
					outPixel = inverseRange * (inPixel - minVal);
					newImage.getProcessor().putPixelValue(x, y, outPixel);
				}
			}
			//newImage.show();
			return newImage;
	}
	
	// Method to update dialog based on user selections
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
			Checkbox IndexColorCheckbox = (Checkbox)gd.getCheckboxes().get(1);
			Vector<?> numericChoices = gd.getNumericFields();
			Vector<?> choices = gd.getChoices();
			if (IndexColorCheckbox.getState()) {
				((TextField)numericChoices.get(0)).setEnabled(true);
				((TextField)numericChoices.get(1)).setEnabled(true);
				((Choice)choices.get(1)).setEnabled(true);
			} 
			else {
				((TextField)numericChoices.get(0)).setEnabled(false);
				((TextField)numericChoices.get(1)).setEnabled(false);
				((Choice)choices.get(1)).setEnabled(false);
			}			
			return true;
		}
}


