import ij.gui.Roi;

public class RoiPair {
	Roi sourceRoi, targetRoi;
	// Constructor
	RoiPair(Roi sourceRoi, Roi targetRoi) {
		this.sourceRoi = sourceRoi;
		this.targetRoi = targetRoi;
	}
	Roi getSourceRoi() {
		return this.sourceRoi;
	}
	Roi getTargetRoi() {
		return this.targetRoi;
	}
}