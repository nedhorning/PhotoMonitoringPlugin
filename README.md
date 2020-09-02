Note that the latest version of Fiji is not compatible with these plugins. It is suggested that you use the “Fiji Life-Line version, 2014 November 25” version of Fiji available from the Fiji website: http://fiji.sc/Downloads. You can also download complete Fiji/ImageJ/Photomonitoring Plugin packages by finding the appropriate package in the "downloads" directory of this Google Drive directory: https://drive.google.com/drive/folders/1wIJO0u8Jvz7zUt3TTDRavXBcL7_wWJot?usp=sharing. These packages contain a version of Fiji that works with the plugins and it has the plugin and associated file already installed. 

To download the plugin, LUTs or guide click on the "downloads" directory then click on the file(s) you want to download. When a gray bar pops up click on the "View Raw" link and the file will be downloaded.

Description: The photo monitoring plugins are written to work with Fiji image processing software (http://fiji.sc/wiki/index.php/Fiji) and they will also work with ImageJ the software on which Fiji is based. These plugins are designed to improve the efficiency and effectiveness of using photo monitoring methods. The plugins are support dual-camera setups with one camera acquiring a "normal" visible color digital photo and the other acquiring a near-infrared digitial photo as well as single camera setups such as infrablue cameras.

There are currently six plugins bundled in PhotoMonitoringPlugin: 1 - The "Create dual image list" plugin is designed to facilitate the process of matching digital photographs that were acquired at roughly the same time. The plugin outputs a text file with the path and file names for image pairs (e.g., images acquired from two cameras) that can be input into the "Dual image NDVI processing" plugin. The image matching is done by synchronizing the times stored in image EXIF DateTimeOriginal tag from each of two cameras. If for some reason the EXIF DateTimeOriginal tag is not set then the files last modified time will be used.

2- The "Dual image index processing" plugin is designed to co-register two images, one using a near-infrared camera and the other a “normal” visible camera. The plugin will work best if the images were acquired from two cameras mounted with their lenses close to each other, acquired at nearly the same time (so the scene hasn't changed), and it's best if the two cameras have similar characteristics such as image size and resolution. The plugin can output the following images:

NGR image (false-color image with r=near-IR, g=green from visible, and r=red from visible)
NDVI or DVI image with a user-selected color table applied
Floating point NDVI or DVI image with actual values (data range -1 to +1 for NDVI or -255 - 255 for DVI)
A visible image clipped to the common area between the registered near-IR and visible image
A log file documenting the registration method used for each image pair
3- The "Single image index from directory" plugin is designed to create color and floating point NDVI or DVI images from a directory containing images that recorded visible light in one band and near-infrared light in another. These images can be captured using the SuperBlue filter available from LifePixel (http://www.lifepixel.com/) or from Public Labs: (http://www.kickstarter.com/projects/publiclab/infragram-the-infrared-photography-project).

4- The "Single image index from displayed image" plugin is designed to create color and floating point NDVI or DVI images from an image displayed in ImageJ/Fiji that recorded visible light in one band and near-infrared light in another. These images can be captured using the SuperBlue filter available from LifePixel (http://www.lifepixel.com/) or from Public Labs: (http://www.kickstarter.com/projects/publiclab/infragram-the-infrared-photography-project).

5- The “Calculate image calibration coefficients” plugin is designed to calibrate digital photograph acquired with a single NIR-modified camera and calibrate a visible bands and  NIR band to reflectance values to facilitate the creation of NDVI and DVI images.

6- The “Apply calibration coefficients to directory of images” plugin is used to apply the calibration coefficients calculated in the  “Calculate image calibration coefficients” plugin to a directory of images and to save the resulting color (JPEG) and or floating point NDVI image (TIFF). 

The .jar file which is the binary form of the plugin can be downloaded by clicking on the Downloads button on the PhotoMonitoringPlugin page on GitHub. Information about how to install and use the plugins is provided in the guide which can be downloaded form this GitHub site.

Notes for developers: The source files necesary to build these plugins are providd on this GitHub respository. The "com" folder contains the .class files from the Meatadata-Extractor project (http://www.drewnoakes.com/code/exif/). The license information and readme file for that project are in the "com" folder. These .class files are necessary to run the photo monitoring plugins.

License: The Photo Monitoring Plugin is an open source initiative and distributed free of charge with no warranty as stipulated in the GNU General Public License. See the GNU license page for more details: http://www.gnu.org/licenses/gpl.html.
