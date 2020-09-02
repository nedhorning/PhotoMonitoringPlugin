import ij.IJ;
import java.io.*;
import java.util.ArrayList;
import com.drew.metadata.*;
import com.drew.imaging.*;
//import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.lang.GeoLocation;

public class MetadataReader {
	File imageFile;
	//  Constructor
	MetadataReader(File imageFile) { 
		this.imageFile = imageFile;
	}
	
	boolean hasGPS() {
		boolean hasGPS = false;
		try {
			Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
			GpsDirectory directory = metadata.getDirectory(GpsDirectory.class);
			if (directory!=null){
				hasGPS = true;
			}
		} catch (ImageProcessingException e) {
			String msg = e.getMessage();
			if (msg==null) msg = ""+e;
			IJ.error("Error extracting GPS metadata from file \n" + this.imageFile.getAbsolutePath()); 
		} 
		catch (IOException e) {
			e.printStackTrace();
			IJ.error("Error extracting GPS metadata from file \n" + this.imageFile.getAbsolutePath());
		}
		return hasGPS;
	}
	
// Create a double array with latitude in the 0 position and longitude in the 1 position
	double[] getLatLon(){
		double[] latLon = new double[2];
		boolean hasTag;
		try {
			Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
			GpsDirectory directory = metadata.getDirectory(GpsDirectory.class);
			if (directory==null){
				latLon = null;
				IJ.error("Error extracting GPS metadata from file \n" + this.imageFile.getAbsolutePath());
				return latLon;
			}
			hasTag = directory.containsTag(GpsDirectory.TAG_GPS_LATITUDE );
			if (!hasTag) {
				latLon = null;
				IJ.error("Error extracting GPS metadata from file \n" + this.imageFile.getAbsolutePath());
			} else {
				//latLon[0] = directory.getDouble(GpsDirectory.TAG_GPS_LATITUDE);
				GeoLocation location = directory.getGeoLocation();
				latLon[0] = location.getLatitude();
				latLon[1] = location.getLongitude();
			}	
			
		} catch (ImageProcessingException e) {
			String msg = e.getMessage();
			if (msg==null) msg = ""+e;
			IJ.error("Error extracting GPS metadata from file \n" + this.imageFile.getAbsolutePath()); 
		} 
		catch (IOException e) {
			e.printStackTrace();
			IJ.error("Error extracting GPS metadata from file \n" + this.imageFile.getAbsolutePath());
		}		
		return latLon;
	}

}