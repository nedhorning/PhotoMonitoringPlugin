import ij.*;
import ij.gui.*;
import ij.process.*;

public class RegImagePair {
	ImagePlus firstImage, secondImage;
	
	// Constructor
	RegImagePair(ImagePlus firstImage, ImagePlus secondImage) {
	      this.firstImage = firstImage;
	      this.secondImage = secondImage;
	}
	
	ImagePlus getFirst() {
		return this.firstImage;
	}
	ImagePlus getSecond() {
		return this.secondImage;
	}

	public ImagePlus calcNDVI (int irChannel, int redChannel, boolean stretchVis, boolean stretchIR, double saturated){
		int width = this.getFirst().getWidth();
		int height = this.getFirst().getHeight();
		int[] lutVis = null;
		int[] lutIR = null;
		double irPixel, visPixel;
		ColorProcessor irImage = (ColorProcessor)this.getFirst().getProcessor();
		ColorProcessor redImage = (ColorProcessor)this.getSecond().getProcessor();
		ImagePlus newImage;
		newImage = NewImage.createFloatImage("ndviImage", width, height, 1, NewImage.FILL_BLACK);
		byte[] irArray = irImage.getChannel(irChannel);
		byte[] redArray = redImage.getChannel(redChannel);
		if (stretchVis)
			lutVis = stretchBand(width, height, redArray, saturated);
		if (stretchIR)
			lutIR = stretchBand(width, height, irArray, saturated);
		double outPixel = 0.0;
		for (int y=0; y<height; y++) {
            int offset = y*width;
			for (int x=0; x<width; x++) {
				int pos = offset+x;
				if (stretchIR)
					irPixel = lutIR[irArray[pos] & 0xff];
				else 
					irPixel = irArray[pos] & 0xff;
				if (stretchVis)
					visPixel = lutVis[redArray[pos] & 0xff];
				else
					visPixel = redArray[pos] & 0xff;
				
				if ((irPixel + visPixel) == 0.0) {
					outPixel = 0.0;
				} else {
				
					outPixel = (irPixel - visPixel)/(irPixel + visPixel);
				}
				newImage.getProcessor().putPixelValue(x, y, outPixel);
			}
		}
		return newImage;		
	}
	
	public ImagePlus calcDVI (int irChannel, int redChannel, boolean stretchVis, boolean stretchIR, double saturated){
		int width = this.getFirst().getWidth();
		int height = this.getFirst().getHeight();
		int[] lutVis = null;
		int[] lutIR = null;
		double irPixel, visPixel;
		ColorProcessor irImage = (ColorProcessor)this.getFirst().getProcessor();
		ColorProcessor redImage = (ColorProcessor)this.getSecond().getProcessor();
		ImagePlus newImage;
		newImage = NewImage.createFloatImage("dviImage", width, height, 1, NewImage.FILL_BLACK);
		byte[] irArray = irImage.getChannel(irChannel);
		byte[] redArray = redImage.getChannel(redChannel);
		if (stretchVis)
			lutVis = stretchBand(width, height, redArray, saturated);
		if (stretchIR)
			lutIR = stretchBand(width, height, irArray, saturated);
		double outPixel = 0.0;
		for (int y=0; y<height; y++) {
            int offset = y*width;
			for (int x=0; x<width; x++) {
				int pos = offset+x;
				if (stretchIR)
					irPixel = lutIR[irArray[pos] & 0xff];
				else 
					irPixel = irArray[pos] & 0xff;
				if (stretchVis)
					visPixel = lutVis[redArray[pos] & 0xff];
				else
					visPixel = redArray[pos] & 0xff;
				
				if ((irPixel + visPixel) == 0.0) {
					outPixel = 0.0;
				} else {
				
					outPixel = (irPixel - visPixel);
				}
				newImage.getProcessor().putPixelValue(x, y, outPixel);
			}
		}
		return newImage;		
	}
	
	public int[] stretchBand (int width, int height, byte[] byteArray, double saturated){
		int threshold;
		int hmin, hmax;
        int max2 = 255;
        int range = 256;
        int[] lut = new int[range];
		ImageStatistics stats = null;
		ByteProcessor imageBP = new ByteProcessor(width, height, byteArray);
		stats = ImageStatistics.getStatistics(imageBP, 16, null);
		int[] histogram = stats.histogram;
		int hsize = histogram.length;
		if (saturated>0.0)
            threshold = (int)(stats.pixelCount*saturated/200.0);
        else
            threshold = 0;
		int i = -1;
        boolean found = false;
        int count = 0;
        int maxindex = hsize-1;
        do {
            i++;
            count += histogram[i];
            found = count>threshold;
        } while (!found && i<maxindex);
        hmin = i;
                
        i = hsize;
        count = 0;
        do {
            i--;
            count += histogram[i];
            found = count>threshold;
        } while (!found && i>0);
        hmax = i;
        
        double min = stats.histMin+hmin*stats.binSize;
        double max = stats.histMin+hmax*stats.binSize;
        for (int x=0; x<range; x++) {
            if (x<=min)
                lut[x] = 0;
            else if (x>=max)
                lut[x] = max2;
            else
                lut[x] = (int)(((double)(x-min)/(max-min))*max2);
        }
        return lut;
	}
	
	
}
