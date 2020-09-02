import ij.IJ;
import java.io.*;
import java.util.ArrayList;
import com.drew.metadata.*;
import com.drew.imaging.*;
import com.drew.metadata.exif.ExifSubIFDDirectory;


public class FilePairList extends ArrayList<FilePair>{
	//ArrayList<FilePair> filePairs;

	private static final long serialVersionUID = 7570377225978123977L;

	// Constructor from two File arrays, time offsets and acceptable time difference
	FilePairList(File[] listFirst_Files, File[] listSecondFiles, long offset, double acceptableDifference) {
		for (int i = 0; i < listSecondFiles.length; i++) { 
			if (listSecondFiles[i].isFile() && !listSecondFiles[i].isHidden()) { 
		        long timeDiff;
		        long bestTimeDiff;
		        long timeFirstFile = 0;
		        long timeSecondFile = 0;
		        String matchingFirst = null;
		        // Find closest matching file last modified time
		        for (int k = 0; k < listFirst_Files.length; k++) {
		        	if (listFirst_Files[k].isFile() && !listFirst_Files[k].isHidden()) {
		        		timeFirstFile = getExifTime(listFirst_Files[k]);
		        		if (timeFirstFile == 0) return;
		        		bestTimeDiff=999999999;
		        	
		        		timeSecondFile = getExifTime(listSecondFiles[i]);
		        		if (timeSecondFile == 0) return;
		        		timeDiff = Math.abs(timeSecondFile - (timeFirstFile + offset));
		               if (timeDiff < bestTimeDiff) {
		             	  bestTimeDiff = timeDiff;
		             	  matchingFirst = listFirst_Files[k].getAbsolutePath();
		               }
		               
		               if (bestTimeDiff <= acceptableDifference) {
		            	   FilePair filePair = new FilePair(matchingFirst, listSecondFiles[i].getAbsolutePath());
		                  this.add(filePair);
		               }
		            }
		        }
		    }    
		}
		this.trimToSize();
	}
	
	// Constructor from a text file
	FilePairList(String dir, String fileName) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(dir+fileName));
			String line;
	      
			line = br.readLine();
			
			while (line!=null) {
				FilePair filePair = new FilePair(line.split(",")[0], line.split(",")[1]);
				this.add(filePair);
				line = br.readLine();
			}
			br.close();
		} catch (Exception e) {
			IJ.error("Error reading file pairs", e.getMessage());
			return;
		}
		
		this.trimToSize();	
	}
	
	// Write contents of FilePairList to a file
	void writeFilePairs(String dir, String fileName) {      
		try { 
			BufferedWriter bw = new BufferedWriter(new FileWriter(dir+fileName));
	         
			for (FilePair photoPair : this) {
				bw.write(photoPair.getFirst()+", "+photoPair.getSecond()+"\n");
			}
			bw.close();
			IJ.showStatus("File with matching pairs created");
		} 
		catch (Exception e) {
		IJ.error("Error writing file pairs", e.getMessage());
		return;
		}
	}
	
	// Get the original image date from the EXIF tag
	long getExifTime(File file){
		long time = 0;
		boolean usingExif = false;
		try {
			Metadata metadata = ImageMetadataReader.readMetadata(file);
			ExifSubIFDDirectory directory = metadata.getDirectory(ExifSubIFDDirectory.class);
			boolean hasTag = directory.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
			if (hasTag) {
				time = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL).getTime();
				usingExif = true;
			}
		} catch (ImageProcessingException e) {
            String msg = e.getMessage();
            if (msg==null) msg = ""+e;
            IJ.error("Error extracting EXIF metadata from file \n" + file.getAbsolutePath()); 
            usingExif = false;
        } 
		catch (IOException e) {
			e.printStackTrace();
			usingExif = false;;
		}
		if (!usingExif) {
			time = file.lastModified();
			usingExif = false;
		}
		
		return time;
	}
}
