package org.droidplanner.android.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;

import com.MAVLink.common.msg_attitude;
import com.MAVLink.common.msg_command_long;
import com.MAVLink.common.msg_set_position_target_local_ned;
import com.MAVLink.enums.MAV_CMD;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.MavlinkObserver;
import com.o3dr.android.client.apis.ExperimentalApi;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.mavlink.MavlinkMessageWrapper;
import com.o3dr.services.android.lib.util.MathUtils;

import org.droidplanner.android.R;
import org.droidplanner.android.fragments.actionbar.ActionBarTelemFragment;
import org.droidplanner.android.fragments.widget.telem.WidgetSoloLinkVideo;
import org.droidplanner.android.widgets.AttitudeIndicator;
import org.droidplanner.android.widgets.JoystickView;
import org.w3c.dom.Attr;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

/**
 * Created by Toby on 8/5/2015.
 */
public class ControlActivity extends DrawerNavigationUI {
    private JoystickView leftJoystick, rightJoystick;
    private static final int ignoreVel = ((1<<3) | (1<<4) | (1 << 5));
    private static final int ignoreAcc = ((1<<6) | (1<<7) | (1 << 8));
    private static final int ignorePos = ((1<<0) | (1<<1) | (1<<2));
    private long lastRecieved;
    private float lastYaw, lastYawSpeed;
    private static final float MAX_VEL = 5f, MAX_VEL_Z = 5f;
    private final static IntentFilter eventFilter = new IntentFilter();
    private VehicleMode mode;
    static{
        eventFilter.addAction(AttributeEvent.ATTITUDE_UPDATED);
        eventFilter.addAction(AttributeEvent.STATE_VEHICLE_MODE);
    }
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getAction()){
                case AttributeEvent.ATTITUDE_UPDATED:
                    attitudeUpdated();
                    break;
                case AttributeEvent.STATE_VEHICLE_MODE:
                    mode = dpApp.getDrone().getAttribute(AttributeType.STATE);
                    break;
            }
        }
    };
    private Fragment actionBarFragment;
    private View flightModeIcon;

    private void attitudeUpdated() {
        Drone drone = dpApp.getDrone();
        if(drone == null)
            return;
        Attitude att = drone.getAttribute(AttributeType.ATTITUDE);
        Home home = drone.getAttribute(AttributeType.HOME);
        Gps gps = drone.getAttribute(AttributeType.GPS);
        if(flightModeIcon == null){
            flightModeIcon = findViewById(R.id.bar_flight_mode_icon);
        }
        if(att != null && gps != null && gps.isValid() && home != null && home.isValid() && flightModeIcon != null){
            double heading = MathUtils.getHeadingFromCoordinates(home.getCoordinate(), gps.getPosition());
            flightModeIcon.animate().rotation((float)((360 + heading - att.getYaw()) % (360))).start();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
        leftJoystick = (JoystickView)findViewById(R.id.left_joystick);
        rightJoystick = (JoystickView)findViewById(R.id.right_joystick);
        Bitmap reticle = BitmapFactory.decodeResource(getResources(), R.drawable.ic_control_grey_600_24dp);
        leftJoystick.setReticle(reticle);
        rightJoystick.setReticle(reticle);
        leftJoystick.setSpring(JoystickView.Axis.X, true);
        leftJoystick.setSpring(JoystickView.Axis.Y, true);
        rightJoystick.setSpring(JoystickView.Axis.Y, true);
        rightJoystick.setSpring(JoystickView.Axis.X, true);
        leftJoystick.setHaptic(JoystickView.Axis.Y, true);
        leftJoystick.setJoystickListener(new JoystickView.JoystickListener() {
            @Override
            public void joystickMoved(float x, float y) {
                float heading = lastYaw + lastYawSpeed * ((System.currentTimeMillis() - lastRecieved) / 1000f);
                sendMove(heading);
                sendYaw(heading);
            }
        });
        rightJoystick.setJoystickListener(new JoystickView.JoystickListener() {
            @Override
            public void joystickMoved(float x, float y) {
                float heading = lastYaw + lastYawSpeed * ((System.currentTimeMillis() - lastRecieved) / 1000f);
                sendMove(heading);
            }
        });
        ScheduledExecutorService scheduledExecutorService =  Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(new Runnable(){
            @Override
            public void run() {
                    float heading = lastYaw + lastYawSpeed * ((System.currentTimeMillis() - lastRecieved)/1000f);
//                    Timber.d("sending yaw");
                    sendYaw(heading);
            }
        }, 0, 33, TimeUnit.MILLISECONDS);


        FragmentManager fm = getSupportFragmentManager();
        actionBarFragment = fm.findFragmentById(R.id.widget_view);
        if(!(actionBarFragment instanceof WidgetSoloLinkVideo)){
            actionBarFragment = new WidgetSoloLinkVideo();
            fm.beginTransaction().replace(R.id.widget_view, actionBarFragment).commit();
        }

    }

    private void sendYaw(float heading) {
        float yaw = leftJoystick.getAxis(JoystickView.Axis.X);
        heading/= Math.PI;
        heading *= 180f;
        if(Math.abs(yaw) > 0.05) {
            msg_command_long msgYaw = new msg_command_long();
            msgYaw.command = MAV_CMD.MAV_CMD_CONDITION_YAW;
            msgYaw.param1 = (360 + (heading + yaw*30f)) % 360;
//            Timber.d("yaw: %f", msgYaw.param1);
            msgYaw.param2 = Math.abs(yaw) * 30f;
            msgYaw.param3 = Math.signum(yaw);
            msgYaw.param4 = 0;
            ExperimentalApi.getApi(dpApp.getDrone()).sendMavlinkMessage(new MavlinkMessageWrapper(msgYaw));
        }
    }

    private void sendMove(float heading){
        float throttle = leftJoystick.getAxis(JoystickView.Axis.Y);
        float x = rightJoystick.getAxis(JoystickView.Axis.X);
        float y = rightJoystick.getAxis(JoystickView.Axis.Y);
        if(mode.equals(VehicleMode.COPTER_GUIDED)) {
            if (x != 0 && y != 0) {
                double theta = Math.atan(y / x);
                if (theta < 0) {
                    theta += Math.PI;
                }
                if (y < 0) {
                    theta += Math.PI;
                }
                theta += Math.PI / 2;
                double magnitude = Math.sqrt(x * x + y * y);
                x = (float) (Math.cos(heading + theta) * magnitude);
                y = (float) (Math.sin(heading + theta) * magnitude);

            }
        }
        msg_set_position_target_local_ned msg = new msg_set_position_target_local_ned();
        msg.vz = throttle * MAX_VEL_Z;
        msg.vy = y *  MAX_VEL;
        msg.vx = x * MAX_VEL;
//        Timber.d("x: %f, y: %f, z: %f", msg.vx, msg.vy, msg.vz);
        msg.type_mask = ignoreAcc | ignorePos;
        ExperimentalApi.getApi(dpApp.getDrone()).sendMavlinkMessage(new MavlinkMessageWrapper(msg));
    }

    @Override
    protected void addToolbarFragment(){
        final int toolbarId = getToolbarId();
        final FragmentManager fm = getSupportFragmentManager();
        Fragment actionBarTelem = fm.findFragmentById(toolbarId);
        if (actionBarTelem == null) {
            actionBarTelem = new ActionBarTelemFragment();
            fm.beginTransaction().add(toolbarId, actionBarTelem).commit();
        }
    }

    @Override
    protected int getToolbarId() {
        return R.id.actionbar_toolbar;
    }

    @Override
    protected int getNavigationDrawerEntryId() {
        return R.id.navigation_control;
    }

    @Override
    public void onApiDisconnected() {
        super.onApiDisconnected();
        getBroadcastManager().unregisterReceiver(receiver);
    }

    @Override
    public void onApiConnected() {
        super.onApiConnected();
        getBroadcastManager().registerReceiver(receiver, eventFilter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        dpApp.getDrone().addMavlinkObserver(new MavlinkObserver() {
            @Override
            public void onMavlinkMessageReceived(MavlinkMessageWrapper mavlinkMessageWrapper) {
                if(mavlinkMessageWrapper.getMavLinkMessage().msgid == msg_attitude.MAVLINK_MSG_ID_ATTITUDE){
                    msg_attitude msg = (msg_attitude)mavlinkMessageWrapper.getMavLinkMessage();
                    lastYaw = msg.yaw;
//                    Timber.d("yaw: %f",lastYaw);
                    lastYawSpeed = msg.yawspeed;
                    lastRecieved = System.currentTimeMillis();
                }
            }
        });
    }
}