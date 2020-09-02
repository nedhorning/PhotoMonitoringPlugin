import ij.*;

import java.io.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.*;
import java.awt.image.BufferedImage;
import org.apache.sanselan.*;
import org.apache.sanselan.common.*;
import org.apache.sanselan.formats.jpeg.JpegImageMetadata;
import org.apache.sanselan.formats.jpeg.exifRewrite.*;
import org.apache.sanselan.formats.tiff.TiffImageMetadata;
import org.apache.sanselan.formats.tiff.write.TiffOutputSet;
import org.apache.sanselan.formats.tiff.write.TiffImageWriterLossless;
import org.apache.sanselan.Sanselan;
import org.apache.sanselan.ImageFormat;
import org.apache.sanselan.formats.tiff.TiffField;
import org.apache.sanselan.formats.tiff.TiffDirectory;

import cafe.image.meta.Metadata;
import cafe.image.meta.MetadataType;

public class WriteEXIF {
	File outImageFile = null;
	File originalJpegFile = null;
	//ImagePlus inImage = null;
	File tempImageFile = null;
	//double[] latLon = null;
	// Constructor
	WriteEXIF(File originalJpegFile, File outImageFile, File tempImageFile) {
		this.originalJpegFile = originalJpegFile;
		this.outImageFile = outImageFile;
		this.tempImageFile = tempImageFile;
	}

    void copyEXIF() {
    	OutputStream os = null;
    	TiffOutputSet outputSet = null;
    	//outputSet = new TiffOutputSet();    
    	JpegImageMetadata jpegMetadata = null;
    	TiffImageMetadata tiffMetadata = null;
    	String extension = originalJpegFile.getName().substring(originalJpegFile.getName().lastIndexOf(".") + 1, originalJpegFile.getName().length());
    	try {
    		final IImageMetadata metadata = Sanselan.getMetadata(originalJpegFile);
    		if (metadata instanceof JpegImageMetadata) {
    			jpegMetadata = (JpegImageMetadata) metadata;
    		} else if (metadata instanceof TiffImageMetadata) {
    			tiffMetadata = (TiffImageMetadata)metadata;
    		}
    		if (extension.equals("tif".toLowerCase())) {
    			tempImageFile.renameTo(outImageFile);
                tempImageFile.delete();
    		} else if (null != jpegMetadata) {
                final TiffImageMetadata exif = jpegMetadata.getExif();
                if (null != exif) {
                    outputSet = exif.getOutputSet();
                }

                os = new FileOutputStream(outImageFile);
                os = new BufferedOutputStream(os);
    		
                new ExifRewriter().updateExifMetadataLossless(tempImageFile, os, outputSet);
                tempImageFile.delete();
    		
            } else if ((null != tiffMetadata)){
            	outputSet = tiffMetadata.getOutputSet();
            	List<TiffField> tiffList = tiffMetadata.getAllFields();
            	List<TiffDirectory> dirList = new ArrayList<TiffDirectory>();
            	dirList = tiffMetadata.getDirectories();
            	outputSet = new TiffOutputSet();
            	
            	for(Object field : tiffMetadata.getAllFields())
                {
                    if(field instanceof TiffField)
                    {
                        TiffField tiffField = (TiffField)field;
                        System.out.println(tiffField.getTagName()+ ": " + tiffField.getValueDescription() + " : " + tiffField.length);
                    }
                }

            	os = new FileOutputStream(outImageFile);
                os = new BufferedOutputStream(os);

                new ExifRewriter().updateExifMetadataLossy(tempImageFile, os, outputSet);
                tempImageFile.delete();
            } else {
    		
           //if (null == outputSet) {
               tempImageFile.renameTo(outImageFile);
           }
    	}
    	catch (ImageWriteException e) {
			e.printStackTrace();
			IJ.error("Error adding GPS metadata to file \n" + this.outImageFile.getAbsolutePath());
		}
    	catch(FileNotFoundException e) {
			e.printStackTrace();
			IJ.error("Error adding GPS metadata to file \n" + this.outImageFile.getAbsolutePath());
    	}
    	catch(IOException e) {
			e.printStackTrace();
			IJ.error("Error adding GPS metadata to file \n" + this.outImageFile.getAbsolutePath());
    	}
    	catch(ImageReadException e) {
			e.printStackTrace();
			IJ.error("Error adding GPS metadata to file \n" + this.outImageFile.getAbsolutePath());
    	}
    }
}

/*	// Based on https://www.mail-archive.com/search?l=user@commons.apache.org&q=subject:%22%5Bimaging%5D%22&o=newest&f=1
void copyExifFromJpeg2Tiff(ImagePlus tiffImage) {
	OutputStream os = null;
	TiffOutputSet outputSet = null;    
	JpegImageMetadata jpegMetadata = null;
	try {
		final IImageMetadata metadata = Sanselan.getMetadata(originalJpegFile);
		if (metadata instanceof JpegImageMetadata) {
			jpegMetadata = (JpegImageMetadata) metadata;
		}
        if (null != jpegMetadata) {
            final TiffImageMetadata exif = jpegMetadata.getExif();
            if (null != exif) {
                outputSet = exif.getOutputSet();
            }
        BufferedImage bufTiffImage = tiffImage.getBufferedImage();
        //TiffOutputDirectory exifDirectory = outputSet.getExifDirectory(); 
		os = new FileOutputStream(outImageFile);
		os = new BufferedOutputStream(os); 
		
		ImageFormat format = ImageFormat.IMAGE_FORMAT_TIFF ;
		//MapString, Object params = new HashMapString, Object();
		Map params = new HashMap ();  
		//Map params = new HashMap(JpegImageParser.TIFF_TAG_IMAGE_LENGTH, new
		byte[] bytes = Sanselan.writeImageToBytes(bufTiffImage, format, params);

		TiffImageWriterLossless writerLossLess = new TiffImageWriterLossless(bytes);
		writerLossLess.write(os, outputSet);
		
		//new ExifRewriter().updateExifMetadataLossless(tempImageFile, os, outputSet);
       }
		
        if (null == outputSet) {
        	IJ.save(tiffImage, outImageFile.getName());
        }
	}
	catch (ImageWriteException e) {
		e.printStackTrace();
		IJ.error("Error adding GPS metadata to file \n" + this.outImageFile.getAbsolutePath());
	}
	catch(FileNotFoundException e) {
		e.printStackTrace();
		IJ.error("Error adding GPS metadata to file \n" + this.outImageFile.getAbsolutePath());
	}
	catch(IOException e) {
		e.printStackTrace();
		IJ.error("Error adding GPS metadata to file \n" + this.outImageFile.getAbsolutePath());
	}
	catch(ImageReadException e) {
		e.printStackTrace();
		IJ.error("Error adding GPS metadata to file \n" + this.outImageFile.getAbsolutePath());
	}
}
*/    