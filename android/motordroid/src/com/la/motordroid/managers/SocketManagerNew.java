package com.la.motordroid.managers;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.text.TextUtils;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.SocketIOClient;
import com.koushikdutta.async.http.SocketIOClient.EventCallback;
import com.koushikdutta.async.http.SocketIOClient.SocketIOConnectCallback;
import com.la.motordroid.App;
import com.la.motordroid.R;
import com.la.motordroid.commons.EventType;
import com.labs.adk.ADKManager;
import com.labs.adk.Callback;
import com.labs.commons.ADK;
import com.labs.commons.SLog;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Amir Lazarovich
 */
public class SocketManagerNew implements SocketIOConnectCallback, Callback, CompletedCallback, EventCallback {
    ///////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////
    private static final String TAG = "SocketManager";
    private static final int PERIOD = 10000; // 10 seconds

    ///////////////////////////////////////////////
    // Members
    ///////////////////////////////////////////////
    private ADKManager mADKManager;
    private Camera mCamera;
    private MediaPlayer mPlayer;
    private Timer mTimer;
    private SocketIOClient mSocket;

    // member-listeners
    private SocketListener mListener;

    ///////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////

    public SocketManagerNew(Context context, SocketListener listener) {
        mListener = listener;
        mADKManager = new ADKManager(context, this);
        initPlayer(context);
        initCamera();


        //Timer getPic = new Timer();
        //getPic.schedule(new TakePicTask(), 1000*3);
    }

    /**
     * Initialize the {@link android.media.MediaPlayer}
     *
     * @param context
     */
    private void initPlayer(Context context) {
        try {
            mPlayer = new MediaPlayer();
            AssetFileDescriptor afd = context.getResources().openRawResourceFd(R.raw.wholelottalove);
            mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setLooping(false);
            mPlayer.prepare();

        } catch (IOException e) {
            SLog.e(TAG, "Couldn't prepare/create media player", e);
        }
    }

    /**
     * Initialize the camera object
     */
    private void initCamera() {
        mCamera = Camera.open();
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setRotation(90);
        mCamera.setParameters(parameters);
        mCamera.setDisplayOrientation(90);
    }

    /**
     * Open connection
     *
     * @param serverAddress
     */
    private void connectToSocket(String serverAddress) {
        if (mSocket != null) {
            SLog.d(TAG, "Already connected to socket");
            return;
        }

        try {
            SocketIOClient.connect(AsyncHttpClient.getDefaultInstance(), serverAddress, this);
//            mSocket = new SocketIO(serverAddress);
//            mSocket.connect(this);
        } catch (Exception e) {
            SLog.e(TAG, "Couldn't open socket", e);
            mListener.onSocketFailure();
        }
    }
    ///////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////

    /**
     * Reconnect to all attached devices
     *
     * @param serverAddress
     */
    public void reconnect(String serverAddress) {
        disconnect();
        connect(serverAddress);
    }


    /**
     * Disconnect from all attached devices
     */
    public void disconnect() {
        if (mADKManager != null) {
            mADKManager.disconnect();
        }

        if (mSocket != null) {
            if (mTimer != null) {
                mTimer.cancel();
            }

            mSocket = null;
//            mSocket.disconnect();
        }
    }



    /**
     * Connect to attached devices
     */
    public void connect(String serverAddress) {
        if (!TextUtils.isEmpty(serverAddress)) {
            mADKManager.connect();
            connectToSocket(serverAddress);
        } else {
            SLog.w(TAG, "Couldn't connect to server since no address was given");
        }
    }

    /**
     * Replace server address
     *
     * @param serverAddress
     */
    public void changeServerAddress(String serverAddress) {
        if (!mADKManager.isConnected()) {
            mADKManager.connect();
        }

        if (mSocket != null) {
            if (mTimer != null) {
                mTimer.cancel();
            }

            mSocket = null;
        }

        connectToSocket(serverAddress);
    }

    ///////////////////////////////////////////////
    // Overrides & Implementations
    ///////////////////////////////////////////////

//    @Override
//    public void onDisconnect() {
//        SLog.d(TAG, "Connection terminated");
//        mListener.onSocketDisconnected();
//    }

    @Override
    public void onConnectCompleted(Exception ex, SocketIOClient client) {
        if (client == null) {
            SLog.e(TAG, "Connection failure!", ex);
            if (mListener != null) {
                mListener.onSocketDisconnected();
            }

            return;
        }

        SLog.d(TAG, "Connection established");
        client.setClosedCallback(this);
        client.setEventCallback(this);

        if (mTimer != null) {
            mTimer.cancel();
        }

        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                SLog.d(TAG, "Trying to keepalive");
                try {
                    HttpClient client = new DefaultHttpClient();
                    HttpGet request = new HttpGet();
                    request.setURI(new URI(App.sConsts.SERVER_ADDRESS + "/keepalive"));
                    client.execute(request);
                } catch (Exception e) {
                    SLog.e(TAG, "Couldn't keepalive", e);
                    mTimer.cancel();
                    mListener.onSocketDisconnected();
                }
            }
        }, 0, PERIOD);

        mListener.onSocketConnected();
    }

    @Override
    public void onCompleted(Exception ex) {
        SLog.d(TAG, "Connection terminated");
        mListener.onSocketDisconnected();
        mSocket = null;
        if (mTimer != null) {
            mTimer.cancel();
        }
    }

    @Override
    public void onEvent(String event, JSONArray arguments) {
//        SLog.d(TAG, "on:: event: %s, args[0]: %s", rawEvent, args[0]);
        for (int i = 0; i < arguments.length(); i++) {
            try {
                SLog.d(TAG, String.valueOf(arguments.get(i)));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

//        Event event = Event.getByValue(rawEvent);
//        EventType eventType = EventType.getByValue(args[0].toString());
//        switch (event) {
//            case CONTROL:
//                if (args.length >= 3) {
//                    Integer firstValue = (Integer) args[1];
//                    Integer secondValue = (Integer) args[2];
//                    if (firstValue != null && secondValue != null) {
//                        SLog.i(TAG, "Control:: [%s], firstValue: %s, secondValue: %s", eventType.getValue(), firstValue, secondValue);
//                        onControlAction(eventType, firstValue, secondValue);
//                    } else {
//                        SLog.w(TAG, "Missing either firstValue or secondValue to process command Control");
//                    }
//                } else {
//                    SLog.w(TAG, "Missing either firstValue or secondValue to process command Control");
//                }
//
//                break;
//
//            case FUNCTION:
//                onFunctionAction(eventType, (args.length > 1) ? args[1] : null);
//                break;
//
//            case KEEP_ALIVE:
//                SLog.d(TAG, "Keeping alive");
//                break;
//
//            default:
//                SLog.w(TAG, "Unknown event received: %s", rawEvent);
//        }
    }

//    public void onConnect() {
//        SLog.d(TAG, "Connection established");
//
//        if (mTimer != null) {
//            mTimer.cancel();
//        }
//
//        mTimer = new Timer();
//        mTimer.scheduleAtFixedRate(new TimerTask() {
//            public void run() {
//                SLog.d(TAG, "Trying to keepalive");
//                try {
//                    HttpClient client = new DefaultHttpClient();
//                    HttpGet request = new HttpGet();
//                    request.setURI(new URI(App.sConsts.SERVER_ADDRESS + "/keepalive"));
//                    client.execute(request);
//                } catch (Exception e) {
//                    SLog.e(TAG, "Couldn't keepalive", e);
//                    mTimer.cancel();
//                    mListener.onSocketDisconnected();
//                }
//            }
//        }, 0, PERIOD);
//
//        mListener.onSocketConnected();
//    }
//
//
//    @Override
//    public void onMessage(String data, IOAcknowledge ack) {
//        SLog.d(TAG, "onMessage");
//    }
//
//
//    @Override
//    public void onMessage(JSONObject json, IOAcknowledge ack) {
//        SLog.d(TAG, "onMessagejson");
//    }

//    @Override
//    public void on(String rawEvent, IOAcknowledge ack, Object... args) {
//        SLog.d(TAG, "on:: event: %s, args[0]: %s", rawEvent, args[0]);
//
//        Event event = Event.getByValue(rawEvent);
//        EventType eventType = EventType.getByValue(args[0].toString());
//        switch (event) {
//            case CONTROL:
//                if (args.length >= 3) {
//                    Integer firstValue = (Integer) args[1];
//                    Integer secondValue = (Integer) args[2];
//                    if (firstValue != null && secondValue != null) {
//                        SLog.i(TAG, "Control:: [%s], firstValue: %s, secondValue: %s", eventType.getValue(), firstValue, secondValue);
//                        onControlAction(eventType, firstValue, secondValue);
//                    } else {
//                        SLog.w(TAG, "Missing either firstValue or secondValue to process command Control");
//                    }
//                } else {
//                    SLog.w(TAG, "Missing either firstValue or secondValue to process command Control");
//                }
//
//                break;
//
//            case FUNCTION:
//                onFunctionAction(eventType, (args.length > 1) ? args[1] : null);
//                break;
//
//            case KEEP_ALIVE:
//                SLog.d(TAG, "Keeping alive");
//                break;
//
//            default:
//                SLog.w(TAG, "Unknown event received: %s", rawEvent);
//        }
//    }

//    @Override
//    public void onError(SocketIOException socketIOException) {
//        SLog.e(TAG, "onError", socketIOException);
//    }

    @Override
    public void onAckReceived(boolean ack) {
        mListener.onAckReceived(ack);
    }

    @Override
    public void onConnected() {
        mListener.onConnected();
    }

    @Override
    public void onDisconnected() {
        mListener.onDisconnected();
    }

    ///////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////

    /**
     * Handle actions directed to the motors
     *
     * @param eventType
     * @param firstValue
     * @param secondValue
     */
    private void onControlAction(EventType eventType, Integer firstValue, Integer secondValue) {
        switch (eventType) {
            case LEFT_STICK:
                sendCommand(ADK.COMMAND_CONTROL,
                        ADK.ACTION_LEFT_STICK,
                        new byte[]{
                                firstValue.byteValue(),
                                secondValue.byteValue()
                        });
                break;

            case RIGHT_STICK:
                sendCommand(ADK.COMMAND_CONTROL,
                        ADK.ACTION_RIGHT_STICK,
                        new byte[]{
                                firstValue.byteValue(),
                                secondValue.byteValue()
                        });
                break;

            default:
                SLog.w(TAG, "Unknown event type detected: %s", eventType);
        }
    }

    /**
     * Handle miscellaneous functions
     *
     * @param eventType
     * @param data
     */
    private void onFunctionAction(EventType eventType, Object data) {
        switch (eventType) {
            case TAKE_PICTURE:
                mCamera.takePicture(shutterCallback, rawCallback, null, jpegCallback);
                break;

            case TOGGLE_MUSIC:
                if (mPlayer.isPlaying()) {
                    mPlayer.stop();
                } else {
                    mPlayer.start();
                }
                break;

            default:
                SLog.w(TAG, "Unknown event type detected: %s", eventType);
        }
    }

    /**
     * Send command to the ADK
     *
     * @param command
     * @param action
     * @param data
     */
    private void sendCommand(final byte command, final byte action, final byte[] data) {
        mADKManager.sendCommand(command, action, data);
        mListener.onSentCommand(command, action, data);
    }


    ///////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////

    Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        public void onShutter() {
            SLog.d(TAG, "onShutter");
        }
    };

    Camera.PictureCallback rawCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            SLog.d(TAG, "onPictureTaken - raw");
        }
    };

    Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            SLog.d(TAG, "onPictureTaken - wrote bytes: " + data.length);
            System.out.print("Length:" + data.length);
            new SentPicTask().execute(data);
            SLog.d(TAG, "onPictureTaken - jpeg");
        }
    };

    /**
     * Send picture taken to the server
     */
    private class SentPicTask extends AsyncTask<byte[], Integer, Long> {

        @Override
        protected Long doInBackground(byte[]... params) {
            SLog.d(TAG, " params[0] wrote bytes: " + params[0].length);

            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost(App.sConsts.SERVER_ADDRESS);

            InputStream dataStream = new ByteArrayInputStream(params[0]);

            try {
                SLog.d(TAG, " before sending 0  " + dataStream.available());
            } catch (IOException e) {
                SLog.e(TAG, "The stream was closed", e);
            }

            InputStreamEntity reqEntity;
            try {
                reqEntity = new InputStreamEntity(dataStream, dataStream.available());
                reqEntity.setContentType("binary/octet-stream");

                SLog.d(TAG, " before sending 1" + reqEntity.getContentLength());

                //reqEntity.setChunked(true); // Send in multiple parts if needed
                httppost.setEntity(reqEntity);
                httpclient.execute(httppost);
            } catch (Exception e) {
                SLog.e(TAG, "Couldn't send image to server", e);
            }

            SLog.d(TAG, "Async op");
            return null;
        }
    }

    /**
     * Take pictures task
     */
    private class TakePicTask extends TimerTask {

        public void run() {
            mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
        }
    }

    public interface SocketListener extends Callback {
        void onSentCommand(byte command, byte action, byte[] data);
        void onSocketFailure();
        void onSocketDisconnected();
        void onSocketConnected();
    }
}
