package com.gorelaxapp.bluetoothtest;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

public class BluetoothConnectionService {

    private static final String TAG = "BluetoothConnectionServ";
    private static final String appName ="bluetoothtest";
    private static final UUID MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private final BluetoothAdapter mBluetoothAdapter;
    Context mContext;

    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    private BluetoothDevice mmdevice;
    private UUID deviceUUID;

    ProgressDialog mProgrssDialog;   //shall use ProgressBar for instead


    public BluetoothConnectionService(Context context) {

        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }


    private class AcceptThread extends Thread {

        private final BluetoothServerSocket mmServerSocket;

        private AcceptThread() {
            BluetoothServerSocket tmp = null;

            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName,MY_UUID_INSECURE);
                Log.d(TAG, "AcceptThread:setting up server using: "+ MY_UUID_INSECURE);
            }catch (IOException e){
                Log.d(TAG, "AcceptThread: IOException: " + e.getMessage());
            }
            mmServerSocket = tmp;
        }//end of AcceptThread class


        public void run(){
            Log.d(TAG, "run: Accept Thread running..." );

            BluetoothSocket socket = null;

            try {
                Log.d(TAG, "run: RFCom server starting..." );
                socket = mmServerSocket.accept();
            }catch (IOException e){
                Log.d(TAG, "AcceptThread: IOException: " + e.getMessage());
            }

            if(socket != null){
                connected (socket , mmdevice); //client / mobile connected to remote device
            }

            Log.i(TAG, "run: End mAcceptThread ");

        }// end of run method

        public void cancel(){

            Log.d(TAG, "cancel: canceling AcceptThread method  ");

            try {
                mmServerSocket.close();
            }catch (IOException e){
                Log.d(TAG, "cancel: close of AcceptThread fail..  " + e.getMessage());
            }//end of try catch

        }//end of method cancel
        
    }// end of class acceptThread


    /**this thread runs while attempting to make connect with a device

     *it run straight thought the connection either success or fails
    */

    private class ConnectThread extends Thread{

       private  BluetoothSocket mmSocket;

    public ConnectThread(BluetoothDevice device, UUID uuid){

        Log.d(TAG, "ConnectThread: start: ");
        mmdevice = device;
        deviceUUID = uuid;

    }
    
    public void run(){
        BluetoothSocket tmp = null;

        Log.i(TAG, "run: mConnectThread ");

        //get Bluetooth socket for a connection with given BluetoothDevice
        
        try {
            Log.d(TAG, "ConnectThread: trying to create InsecureRFcomm socket using UUID  " + MY_UUID_INSECURE);
            tmp = mmdevice.createInsecureRfcommSocketToServiceRecord(deviceUUID);
        }catch (IOException e){
            Log.e(TAG, "ConnectThread: can't create InsecureRFcommSocket " + e.getMessage());
        }

        mmSocket = tmp;

        //always cancel the discovery because it will slow down a connection
        mBluetoothAdapter.cancelDiscovery();

       try {
            //this is a blocking call and only reture a success connection or exception
            mmSocket.connect();
           Log.d(TAG, "ConnectThread: connected  " );
       }catch (IOException e){
           try {
               mmSocket.connect();
               Log.d(TAG, "ConnectThread: close connected  " );
           }catch (IOException e1){
               Log.e(TAG, "ConnectThread: unalbe to close connect in socket " + e.getMessage() );
           }
           Log.d(TAG, "ConnectThread: can not connect to UUID:" + MY_UUID_INSECURE);
       }
       
        connected(mmSocket, mmdevice); //remote device/wearRing/server connected to client

     } //end of run()

        public void cancel(){

            try {
                Log.d(TAG, "cancel: canceling client Socket  ");
                mmSocket.close();
            }catch (IOException e){
                Log.e(TAG, "cancel: close of  mmsocket in connection  fail..  " + e.getMessage());

            }//end of try catch

        }//end of method cancel


    }//end of ConnectThread Class

    public synchronized void start(){

        Log.d(TAG, "start: ");
        if(mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if(mInsecureAcceptThread == null){

            mInsecureAcceptThread = new AcceptThread();
            mInsecureAcceptThread.start();
        }

    } //end of synchronized start method

    public void  startClient(BluetoothDevice device, UUID uuid){

        Log.d(TAG, "startClient:Start. ");
        mProgrssDialog = ProgressDialog.show(mContext,"connecting Bluetooth", "Please Wait...", true);
        mConnectThread = new ConnectThread(device, uuid);
        mConnectThread.start();

    }//end of startClient method

    private class ConnectedThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread (BluetoothSocket socket){

            Log.d(TAG, "ConnectedThread: starting");

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream temOut = null;

            //dismiss the Progress Dialog when connection is success
            mProgrssDialog.dismiss();

            try {
                tmpIn = mmSocket.getInputStream();
                temOut = mmSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmInStream = tmpIn;
            mmOutStream = temOut;

        }//end of Connected Tread method

        public void run(){

            byte[] buffer = new byte[1024]; //buffer store for the steam
            int bytes; // bytes return from read()

            while (true){
                try {
                    bytes = mmInStream.read(buffer);
                    String inComingMessage = new String(buffer, 0, bytes);
                    Log.d(TAG, "IncomeStream: "+ inComingMessage);
                } catch (IOException e) {
                    Log.e(TAG, "write: error reading input stream: "+e.getMessage() );
                    break;
                }


            }//end of while loop

        }//end of run method

        //call this from the main Activity to send data to the remote device
        public void write(byte[] bytes){
            String text = new String(bytes, Charset.defaultCharset());
            Log.d(TAG, "write: writing the output string:" + text);

            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "write: error writing with Outputstream: "+e.getMessage() );
            }

        }//end of write menthod

        //call this from the main Activity to shut down connection
        public void cancel(){
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }//end of cancel method

    } //end of ConnectedThread Class

    private void connected(BluetoothSocket mmSocket, BluetoothDevice mmdevice) {
        Log.d(TAG, "connected: starting");

        //start the thread and manage the connection and perform transmission
        mConnectedThread = new ConnectedThread(mmSocket);
        mConnectedThread.start();
    }

    //write to the ConnectedThread in an unsynchronized manner
    //out the bytes to write
    //connectedThread #write
    public  void write(byte[] out){

        ConnectedThread r;


        //synchronize a copy of the connectedThread
        Log.d(TAG, "write: write Called.");

        //perform the write unsynchronized
        mConnectedThread.write(out);

    }//end of write method

    //this is testing

}// end of main class
