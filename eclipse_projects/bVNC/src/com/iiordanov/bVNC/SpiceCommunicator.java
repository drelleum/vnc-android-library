package com.iiordanov.bVNC;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;

import com.freerdp.freerdpcore.services.LibFreeRDP.UIEventListener;
import com.iiordanov.bVNC.input.RdpKeyboardMapper;
import com.iiordanov.bVNC.input.RemoteKeyboard;
import com.iiordanov.bVNC.input.RemoteSpicePointer;
import com.gstreamer.*;

public class SpiceCommunicator implements RfbConnectable, RdpKeyboardMapper.KeyProcessingListener {
    private final static String TAG = "SpiceCommunicator";

    public native int  SpiceClientConnect (String ip, String port, String tport, String password, String ca_file, String cert_subj, boolean sound);
    public native void SpiceClientDisconnect ();
    public native void SpiceButtonEvent (int x, int y, int metaState, int pointerMask);
    public native void SpiceKeyEvent (boolean keyDown, int virtualKeyCode);
    public native void UpdateBitmap (Bitmap bitmap, int x, int y, int w, int h);
    public native void SpiceRequestResolution (int x, int y);
    
    static {
        System.loadLibrary("gstreamer_android");
        System.loadLibrary("spice");
    }
    
    final static int LCONTROL = 29;
    final static int RCONTROL = 285;
    final static int LALT = 56;
    final static int RALT = 312;
    final static int LSHIFT = 42;
    final static int RSHIFT = 54;
    final static int LWIN = 347;
    final static int RWIN = 348;

    int metaState = 0;
    
    private int width = 0;
    private int height = 0;
    
    boolean isInNormalProtocol = false;
    
    private SpiceThread spicehread = null;

    public SpiceCommunicator (Context context, RemoteCanvas canvas, ConnectionBean connection) {
        if (connection.getEnableSound()) {
            try {
                GStreamer.init(context);
            } catch (Exception e) {
                e.printStackTrace();
                canvas.displayShortToastMessage(e.getMessage());
            }
        }
    }

    private static UIEventListener uiEventListener = null;
    private Handler handler = null;

    public void setHandler(Handler handler) {
        this.handler = handler;
    }
    
    public void setUIEventListener(UIEventListener ui) {
        uiEventListener = ui;
    }

    public Handler getHandler() {
        return handler;
    }

    public void connect(String ip, String port, String tport, String password, String cf, String cs, boolean sound) {
        //android.util.Log.e(TAG, ip + ", " + port + ", " + tport + ", " + password + ", " + cf + ", " + cs);
        spicehread = new SpiceThread(ip, port, tport, password, cf, cs, sound);
        spicehread.start();
    }
    
    public void disconnect() {
        SpiceClientDisconnect();
        try {spicehread.join(3000);} catch (InterruptedException e) {}
    }

    class SpiceThread extends Thread {
        private String ip, port, tport, password, cf, cs;
        boolean sound;

        public SpiceThread(String ip, String port, String tport, String password, String cf, String cs, boolean sound) {
            this.ip = ip;
            this.port = port;
            this.tport = tport;
            this.password = password;
            this.cf = cf;
            this.cs = cs;
            this.sound = sound;
        }

        public void run() {
            SpiceClientConnect (ip, port, tport, password, cf, cs, sound);
            android.util.Log.e(TAG, "SpiceClientConnect returned.");

            // If we've exited SpiceClientConnect, the connection was
            // interrupted or was never established.
            if (handler != null) {
                handler.sendEmptyMessage(Constants.SPICE_CONNECT_FAILURE);
            }
        }
    }
    
    public void sendMouseEvent (int x, int y, int metaState, int pointerMask) {
        SpiceButtonEvent(x, y, metaState, pointerMask);
    }

    public void sendKeyEvent (boolean keyDown, int virtualKeyCode) {
        SpiceKeyEvent(keyDown, virtualKeyCode);
    }
    
    
    /* Callbacks from jni */
    private static void OnSettingsChanged(int inst, int width, int height, int bpp) {
        if (uiEventListener != null)
            uiEventListener.OnSettingsChanged(width, height, bpp);
    }

    private static boolean OnAuthenticate(int inst, StringBuilder username, StringBuilder domain, StringBuilder password) {
        if (uiEventListener != null)
            return uiEventListener.OnAuthenticate(username, domain, password);
        return false;
    }

    private static boolean OnVerifyCertificate(int inst, String subject, String issuer, String fingerprint) {
        if (uiEventListener != null)
            return uiEventListener.OnVerifiyCertificate(subject, issuer, fingerprint);
        return false;
    }

    private static void OnGraphicsUpdate(int inst, int x, int y, int width, int height) {
        if (uiEventListener != null)
            uiEventListener.OnGraphicsUpdate(x, y, width, height);
    }

    private static void OnGraphicsResize(int inst, int width, int height, int bpp) {
        android.util.Log.e("Connector", "onGraphicsResize, width: " + width + " height: " + height);
        if (uiEventListener != null)
            uiEventListener.OnGraphicsResize(width, height, bpp);
    }
    
    @Override
    public int framebufferWidth() {
        return width;
    }

    @Override
    public int framebufferHeight() {
        return height;
    }

    public void setFramebufferWidth(int w) {
        width = w;
    }

    public void setFramebufferHeight(int h) {
        height = h;
    }
    
    @Override
    public String desktopName() {
        // TODO Auto-generated method stub
        return "";
    }

    @Override
    public void requestUpdate(boolean incremental) {
        // TODO Auto-generated method stub

    }

    @Override
    public void writeClientCutText(String text) {
        // TODO Auto-generated method stub

    }
    
    @Override
    public void setIsInNormalProtocol(boolean state) {
        isInNormalProtocol = state;        
    }
    
    @Override
    public boolean isInNormalProtocol() {
        return isInNormalProtocol;
    }

    @Override
    public String getEncoding() {
        // TODO Auto-generated method stub
        return "";
    }

    @Override
    public void writePointerEvent(int x, int y, int metaState, int pointerMask) {
        this.metaState = metaState; 
        if ((pointerMask & RemoteSpicePointer.PTRFLAGS_DOWN) != 0)
            sendModifierKeys(true);
        sendMouseEvent(x, y, metaState, pointerMask);
        if ((pointerMask & RemoteSpicePointer.PTRFLAGS_DOWN) == 0)
            sendModifierKeys(false);
    }

    private void sendModifierKeys(boolean keyDown) {        
        if ((this.metaState & RemoteKeyboard.CTRL_MASK) != 0) {
            android.util.Log.e("SpiceCommunicator", "Sending CTRL: " + LCONTROL + " down: " + keyDown);
            sendKeyEvent(keyDown, LCONTROL);
        }
        if ((this.metaState & RemoteKeyboard.ALT_MASK) != 0) {
            android.util.Log.e("SpiceCommunicator", "Sending ALT: " + LALT + " down: " + keyDown);
            sendKeyEvent(keyDown, LALT);
        }
        if ((this.metaState & RemoteKeyboard.ALTGR_MASK) != 0) {
            android.util.Log.e("SpiceCommunicator", "Sending ALTGR: " + RALT + " down: " + keyDown);
            sendKeyEvent(keyDown, RALT);
        }
        if ((this.metaState & RemoteKeyboard.SUPER_MASK) != 0) {
            android.util.Log.e("SpiceCommunicator", "Sending SUPER: " + LWIN + " down: " + keyDown);
            sendKeyEvent(keyDown, LWIN);
        }
        if ((this.metaState & RemoteKeyboard.SHIFT_MASK) != 0) {
            android.util.Log.e("SpiceCommunicator", "Sending SHIFT: " + LSHIFT + " down: " + keyDown);
            sendKeyEvent(keyDown, LSHIFT);
        }
    }
    
    @Override
    public void writeKeyEvent(int key, int metaState, boolean keyDown) {
        if (keyDown) {
            this.metaState = metaState;
            sendModifierKeys (true);
        }
        
        android.util.Log.e("SpiceCommunicator", "Sending scanCode: " + key + ". Is it down: " + keyDown);
        sendKeyEvent(keyDown, key);
        
        if (!keyDown) {
            sendModifierKeys (false);
            this.metaState = metaState;
        }
    }

    @Override
    public void writeSetPixelFormat(int bitsPerPixel, int depth,
            boolean bigEndian, boolean trueColour, int redMax, int greenMax,
            int blueMax, int redShift, int greenShift, int blueShift,
            boolean fGreyScale) {
        // TODO Auto-generated method stub

    }

    @Override
    public void writeFramebufferUpdateRequest(int x, int y, int w, int h,
            boolean b) {
        // TODO Auto-generated method stub

    }

    @Override
    public void close() {
        disconnect();
    }
    
    // ****************************************************************************
    // KeyboardMapper.KeyProcessingListener implementation
    @Override
    public void processVirtualKey(int virtualKeyCode, boolean keyDown) {

        if (keyDown)
            sendModifierKeys (true);
        
        //android.util.Log.e("SpiceCommunicator", "Sending VK key: " + virtualKeyCode + ". Is it down: " + down);
        sendKeyEvent(keyDown, virtualKeyCode);
        
        if (!keyDown)
            sendModifierKeys (false);
        
    }

    @Override
    public void processUnicodeKey(int unicodeKey) {
        boolean addShift = false;
        int keyToSend = -1;
        int tempMeta = 0;
        
        // Workarounds for some pesky keys.
        if (unicodeKey == 64) {
            addShift = true;
            keyToSend = 0x32;
        } else if (unicodeKey == 42) {
                addShift = true;
                keyToSend = 0x38;
        } else if (unicodeKey == 47) {
            keyToSend = 0xBF;
        } else if (unicodeKey == 63) {
            addShift = true;            
            keyToSend = 0xBF;
        }
        
        if (keyToSend != -1) {
            tempMeta = metaState;
            if (addShift) {
                metaState = metaState |  RemoteKeyboard.SHIFT_MASK;
            }
            processVirtualKey(keyToSend, true);
            processVirtualKey(keyToSend, false);
            metaState = tempMeta;
        } else
            android.util.Log.e("SpiceCommunicator", "Unsupported unicode key that needs to be mapped: " + unicodeKey);
    }

    @Override
    public void switchKeyboard(int keyboardType) {
        // This is functionality specific to aFreeRDP.
    }

    @Override
    public void modifiersChanged() {
        // This is functionality specific to aFreeRDP.
    }
    
    @Override
    public void requestResolution(int x, int y) {
        SpiceRequestResolution (x, y);        
    }
}
