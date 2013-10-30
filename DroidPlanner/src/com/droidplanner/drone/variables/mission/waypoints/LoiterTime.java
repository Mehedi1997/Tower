package com.droidplanner.drone.variables.mission.waypoints;

import com.droidplanner.dialogs.mission.DialogMission;
import com.droidplanner.dialogs.mission.DialogMissionLoiterT;
import com.droidplanner.fragments.markers.MarkerManager.MarkerSource;
import com.google.android.gms.maps.model.LatLng;

public class LoiterTime extends Loiter implements MarkerSource {
	double time;
	
	public LoiterTime(LatLng coord, double altitude) {
		super(coord, altitude);
	}

	public double getTime() {
		return time;
	}

	public void setTime(double time) {
		this.time = time;
	}
	
	@Override
	public DialogMission getDialog() {
		return new DialogMissionLoiterT();
	}
	
}