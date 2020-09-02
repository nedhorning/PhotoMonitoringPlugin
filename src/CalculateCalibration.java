import ij.*;
import ij.io.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.CurveFitter;
import ij.plugin.*;
import ij.plugin.frame.RoiManager;
import ij.Prefs;
import ij.gui.DialogListener;
import java.awt.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Vector;


public class CalculateCalibration implements PlugIn, DialogListener {
	public void run(String arg) {
		// Dialog variables
		String[] IndexBands = {"red", "green", "blue"};
		Boolean saveParameters = true;
		Boolean useDefaults = false;
		Roi[] rois = null;
		String logName = "log.txt";
		double[] visImageValues = null;
		double[] nirImageValues = null;
		double[] visRefValues = null;
		double[] nirRefValues = null;
		double[] visRegressionParams = null;
		double[] nirRegressionParams = null;
		double visR_Squared = 0.0;
		double nirR_Squared = 0.0;
		
		
		RoiManager manager = RoiManager.getInstance();
		if (manager == null) {
			IJ.error("At least 2 ROIs must be added to the ROI Tool before running plugin");
	    	return;
		}
		rois = manager.getRoisAsArray();
		if (rois.length < 2) {
			IJ.error("At least 2 ROIs must be added to the ROI Tool before running plugin");
	    	return;
		}
		ImagePlus imp = IJ.getImage();

		// Initialize variables from IJ.Prefs file
		int visBandIndex = (int)Prefs.get("pm.calibrate.visBandIndex", 0); 
		int nirBandIndex = (int)Prefs.get("pm.calibrate.nirBandIndex", 2);
		Boolean subtractNIR = Prefs.get("pm.calibrate.subtractNIR", true);
		double percentToSubtract = Prefs.get("pm.calibrate.percentToSubtract", 80.0);
		Boolean removeGamma = Prefs.get("pm.calibrate.removeGamma", false);
		double gamma = Prefs.get("pm.calibrate.gamma", 2.2);
		//Boolean applyToOtherImages = Prefs.get("pm.calibrate.applyToOtherImages", false);
		//Boolean writeGainOffset = Prefs.get("pm.calibrate.writeGainOffset", true);
		
		// Create dialog window
		GenericDialog dialog = new GenericDialog("Enter variables");
		dialog.addCheckbox("Load default parameters (click OK below to reload)", false);
		dialog.addChoice("Channel for Visible band to create Index", IndexBands, IndexBands[visBandIndex]);
		dialog.addChoice("Channel for IR band to create Index", IndexBands, IndexBands[nirBandIndex]);
		dialog.addCheckbox("Subtract NIR from visible?", subtractNIR);
		dialog.addNumericField("Percent of NIR to subtract (enter values between 0 and 100)", percentToSubtract, 3);
		dialog.addCheckbox("Remove gamma effect?", removeGamma);
		dialog.addNumericField("Gamma value", gamma, 5);	
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
			dialog.addChoice("Channel for Visible band to create Index", IndexBands, IndexBands[0]);
			dialog.addChoice("Channel for IR band to create Index", IndexBands, IndexBands[2]);
			dialog.addCheckbox("Subtract NIR from visible?", true);
			dialog.addNumericField("Percent of NIR to subtract (enter values between 0 and 100)", 80, 3);
			dialog.addCheckbox("Remove gamma effect?", false);
			dialog.addNumericField("Factor for removing gamma", 2.2, 5);
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
		visBandIndex = dialog.getNextChoiceIndex();
		nirBandIndex = dialog.getNextChoiceIndex();
		subtractNIR = dialog.getNextBoolean();
		percentToSubtract = dialog.getNextNumber();
		removeGamma = dialog.getNextBoolean();
		gamma = dialog.getNextNumber();
		saveParameters  = dialog.getNextBoolean();
		
		if (saveParameters) {
			// Set preferences to IJ.Prefs file
			Prefs.set("pm.calibrate.visBandIndex", visBandIndex);
			Prefs.set("pm.calibrate.nirBandIndex", nirBandIndex);
			Prefs.set("pm.calibrate.subtractNIR", subtractNIR);
			Prefs.set("pm.calibrate.percentToSubtract", percentToSubtract);
			Prefs.set("pm.calibrate.removeGamma", removeGamma);
			Prefs.set("pm.calibrate.gamma", gamma);
	
			// Save preferences to IJ.Prefs file
			Prefs.savePreferences();
		}
			
		// Dialog for calibration info output file
		SaveDialog sd = new SaveDialog("Output calibration file name and directory", "calibration", ".txt");
		String outDirectory = sd.getDirectory();
		logName = sd.getFileName();
		if (logName==null){
			IJ.error("No directory was selected");
			return;
		}

		// Dialog to open target reference CSV file
		OpenDialog od = new OpenDialog("Target reference data", arg);
		String targetDirectory = od.getDirectory();
		String targetFileName = od.getFileName();
		if (targetFileName==null) {
		    IJ.error("No file was selected");
		    return;
		}
		    
		// Open target reference file, read each line and fill vis and nir target reference arrays
	    BufferedReader fileReader = null;
	    int numLines = 0;
	    String line = null;
	    try {
	         String fullLine = "";
	         fileReader = new BufferedReader(new FileReader(targetDirectory+targetFileName));	            
	         // Read line to count the number of lines
	         while ((line = fileReader.readLine()) != null) {
	        	 if (line.length() > 0) {
	        		 numLines++;
	        	 }
	         }
	         fileReader.close();
	         fileReader = new BufferedReader(new FileReader(targetDirectory+targetFileName));
	         visImageValues = new double[numLines];
	         nirImageValues = new double[numLines];
	         visRefValues = new double[numLines];
	         nirRefValues = new double[numLines];
	             
	    	//Read lines to get target data
	    	int counter = 0;
	    	//while ((fullLine = fileReader.readLine()) != null) {
	    	for (int i=0; i < numLines; i++) {
	    		//Parse each line into target reference values
	    		fullLine = fileReader.readLine();
	    		String[] dataValues = fullLine.split(",");
	    		visRefValues[counter] = (Double.parseDouble(dataValues[0]));
	    		nirRefValues[counter] = (Double.parseDouble(dataValues[1]));
	    		counter++;
	    	}
	    }
	    catch (Exception e) {
	    	IJ.error("Error reading target reference data", e.getMessage());
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
	
		double visPixel = 0.0;
		double nirPixel = 0.0;
		double outPixel = 0.0;
		
/*		int maxScaleValue = 0;
		if(imp.getType() == ImagePlus.COLOR_RGB) {
			maxScaleValue = 255;
		} else {
			maxScaleValue = 65535;
		}*/
		
		if (imp.getNChannels() == 1) {
			imp = new CompositeImage(imp);
		//	IJ.run("Make Composite");
	//		IJ.run("16-bit");
		//	imp = IJ.getImage();
		}
	
		// Split image into individual bands
		ImagePlus[] imageBands = ChannelSplitter.split(imp);
		ImagePlus visImage = scaleImage(imageBands[visBandIndex], "visImage");
		ImagePlus nirImage = scaleImage(imageBands[nirBandIndex], "nirImage");
		//visImage.updateAndDraw();
		//visImage.show();
		if(removeGamma){
			//double undoGamma = 1/gamma;
			for (int y=0; y<nirImage.getHeight(); y++) {
				for (int x=0; x<nirImage.getWidth(); x++) {	
					//nirPixel = nirImage.getProcessor().getPixelValue(x, y);
					//visPixel = visImage.getProcessor().getPixelValue(x, y);
/*					if(nirPixel <= 0.03928) {
						nirPixel = nirPixel/12.92;
					} else {
						nirPixel = Math.pow((nirPixel+0.055)/1.055, 2.4);
					}
					if(visPixel <= 0.03928) {
						visPixel = visPixel/12.92;
					} else {
						visPixel = Math.pow((visPixel+0.055)/1.055, 2.4);
					}*/
					nirPixel = Math.pow((nirImage.getProcessor().getPixelValue(x, y)), gamma);
					visPixel = Math.pow((visImage.getProcessor().getPixelValue(x, y)), gamma);
					visImage.getProcessor().putPixelValue(x, y, visPixel);
					nirImage.getProcessor().putPixelValue(x, y, nirPixel);
				}
			}
		}
		//visImage.updateAndDraw();
		//visImage.show();	
		if(subtractNIR){
			// Subtract some of the NIR from visible band
			percentToSubtract = percentToSubtract / 100.0;
			for (int y=0; y<nirImage.getHeight(); y++) {
				for (int x=0; x<nirImage.getWidth(); x++) {					
					nirPixel = nirImage.getProcessor().getPixelValue(x, y);
					visPixel = visImage.getProcessor().getPixelValue(x, y) - (percentToSubtract * nirPixel);
					visImage.getProcessor().putPixelValue(x, y, visPixel);
				}
			}
		}
			
		//visImage.updateAndDraw();
		//visImage.show();			
		// Calculate mean under ROIs for visible band
		// Code from Calculate_Mean.java written by Wayne Rasband
		for (int i=0; i<rois.length; i++) {
			Roi roi = rois[i];
			if (roi!=null && !roi.isArea()) roi = null;
			ImageProcessor ip = visImage.getProcessor();
			ImageProcessor mask = roi!=null?roi.getMask():null;
			Rectangle r = roi!=null?roi.getBounds():new Rectangle(0,0,ip.getWidth(),ip.getHeight());
			double sum = 0;
			int count = 0;
			for (int y=0; y<r.height; y++) {
				for (int x=0; x<r.width; x++) {
					if (mask==null||mask.getPixel(x,y)!=0) {
						count++;
						sum += ip.getPixelValue(x+r.x, y+r.y);
					}
				}
			}
			IJ.log("count: "+count);
			IJ.log("mean: "+IJ.d2s(sum/count,4));
			visImageValues[i] = sum/count;
		}
			
		// Calculate mean under ROIs for NIR band
		for (int i=0; i<rois.length; i++) {
			Roi roi = rois[i];
			if (roi!=null && !roi.isArea()) roi = null;
			ImageProcessor ip = nirImage.getProcessor();
			ImageProcessor mask = roi!=null?roi.getMask():null;
			Rectangle r = roi!=null?roi.getBounds():new Rectangle(0,0,ip.getWidth(),ip.getHeight());
			double sum = 0;
			int count = 0;
			for (int y=0; y<r.height; y++) {
				for (int x=0; x<r.width; x++) {
					if (mask==null||mask.getPixel(x,y)!=0) {
						count++;
						sum += ip.getPixelValue(x+r.x, y+r.y);
					}
				}
			}
			IJ.log("count: "+count);
			IJ.log("mean: "+IJ.d2s(sum/count,4));
			nirImageValues[i] = sum/count;
		}
						
		CurveFitter visRegression = new CurveFitter(visImageValues, visRefValues);
		visRegression.doFit(CurveFitter.STRAIGHT_LINE, true);
		visRegressionParams = visRegression.getParams();
		visR_Squared = visRegression.getRSquared();
		IJ.log(("intercept: "+IJ.d2s(visRegressionParams[0],8)));
		IJ.log(("slope: "+IJ.d2s(visRegressionParams[1],8)));
		
		PlotWindow.noGridLines = false;
		//Plot visPlot = new Plot("Visible band regression", "Image values", "Reflectance values", visImageValues, visRefValues);
		Plot visPlot = new Plot("Visible band regression", "Image values", "Reflectance values");
		visPlot.setLimits(0, 1, 0, 1);
		visPlot.setColor(Color.RED);
		visPlot.addPoints(visImageValues, visRefValues, Plot.CIRCLE);
		visPlot.draw();
		double xVis[] = {0, 1};
		double yVis[] = {visRegressionParams[0], visRegressionParams[1] + visRegressionParams[0]};
		visPlot.addPoints(xVis,yVis,PlotWindow.LINE);
		visPlot.addLabel(0.05, 0.1, "R squared = " + Double.toString(visR_Squared));
		visPlot.show();
			
		CurveFitter nirRegression = new CurveFitter(nirImageValues, nirRefValues);
		nirRegression.doFit(CurveFitter.STRAIGHT_LINE, true);
		nirRegressionParams = nirRegression.getParams();
		nirR_Squared = nirRegression.getRSquared();
		IJ.log(("intercept: "+IJ.d2s(nirRegressionParams[0],8)));
		IJ.log(("slope: "+IJ.d2s(nirRegressionParams[1],8)));
		
		PlotWindow.noGridLines = false;
		Plot nirPlot = new Plot("NIR band regression", "Image values", "Reflectance values");
		nirPlot.setLimits(0, 1, 0, 1);
		nirPlot.setColor(Color.RED);
		nirPlot.addPoints(nirImageValues, nirRefValues, Plot.CIRCLE);
		nirPlot.draw();
		double xNir[] = {0, 1};
		double yNir[] = {nirRegressionParams[0], nirRegressionParams[1] + nirRegressionParams[0]};
		nirPlot.addPoints(xNir,yNir,PlotWindow.LINE);
		nirPlot.addLabel(0.05, 0.1, "R squared = " + Double.toString(nirR_Squared));
		nirPlot.show();
		
			
		try {
		   	BufferedWriter bufWriter = new BufferedWriter(new FileWriter(outDirectory+logName));
		   	bufWriter.write("Calibration information for "+imp.getTitle()+"\n");
		   	bufWriter.write("\n");
		   	bufWriter.write("Number of data points for regression: "+numLines+"\n");
		   	bufWriter.write("R squared for visible band: "+visR_Squared+"\n");
		   	bufWriter.write("R squared for NIR band: "+nirR_Squared+"\n");
		   	bufWriter.write("\n");
		   	bufWriter.write("Visible band slope (gain) and intercept (offest) \n");
		   	bufWriter.write( "   intercept: "+IJ.d2s(visRegressionParams[0],8)+"\n");
		   	bufWriter.write( "   slope: "+IJ.d2s(visRegressionParams[1],8)+"\n");
		   	bufWriter.write("NIR band slope (gain) and intercept (offest) \n");
		   	bufWriter.write( "   intercept: "+IJ.d2s(nirRegressionParams[0],8)+"\n");
		   	bufWriter.write( "   slope: "+IJ.d2s(nirRegressionParams[1],8)+"\n");
		   	bufWriter.write("\n");
		    bufWriter.write("Subtract NIR from visible:"+subtractNIR + "\n");
		    bufWriter.write("Percent of NIR to subtract: "+percentToSubtract*100 + "\n");
		    bufWriter.write("Remove gamma effect:"+removeGamma + "\n");
		    bufWriter.write("Gamma factor: "+gamma + "\n");
		    bufWriter.write("\n");
		    bufWriter.write("Visible band: " + (visBandIndex+1) + "\n");
		    bufWriter.write("Near-infrared band: " + (nirBandIndex+1) + "\n");
		    bufWriter.write("\n");
		   	for (int i=0; i<numLines; i++) {
		   		bufWriter.write("Mean for target "+(i+1)+" for visible band: "+visImageValues[i]+"\n");
		   		bufWriter.write("Mean for target "+(i+1)+" for NIR band: "+nirImageValues[i]+"\n");
		   	}
		   	bufWriter.close();
		} catch (Exception e) {
		    IJ.error("Error writing log file", e.getMessage());
		    return;
		}

		ImagePlus newImage;
		newImage = NewImage.createFloatImage("ndviImage", nirImage.getWidth(), nirImage.getHeight(), 1, NewImage.FILL_BLACK);
		for (int y=0; y<nirImage.getHeight(); y++) {
			for (int x=0; x<nirImage.getWidth(); x++) {
				nirPixel = nirImage.getProcessor().getPixelValue(x, y) * nirRegressionParams[1] + nirRegressionParams[0];
				visPixel = (visImage.getProcessor().getPixelValue(x, y) * visRegressionParams[1] + visRegressionParams[0]);
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
		newImage.show();
		// Close the original image 
		imp.changes = false;
		imp.close();
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
			return newImage;
	}
	

	// Method to update dialog based on user selections
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
			Checkbox SubtractNIRCheckbox = (Checkbox)gd.getCheckboxes().get(1);
			Checkbox RemoveGammaCheckbox = (Checkbox)gd.getCheckboxes().get(2);
			Vector<?> numericChoices = gd.getNumericFields();
			if (SubtractNIRCheckbox.getState()) {
				((TextField)numericChoices.get(0)).setEnabled(true);
			} 
			else {
				((TextField)numericChoices.get(0)).setEnabled(false);
			}
			if (RemoveGammaCheckbox.getState()) {
				((TextField)numericChoices.get(1)).setEnabled(true);
			} 
			else {
				((TextField)numericChoices.get(1)).setEnabled(false);
			}
			return true;
		}
}