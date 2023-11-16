package com.example.bluetoothchat;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ArrayAdapter;
import com.example.bluetoothchat.databinding.ActivityMainBinding;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    BluetoothAdapter bluetoothAdapter;

    BluetoothDevice[] btArray;

    SendReceive sendReceive;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING=2;
    static final int STATE_CONNECTED=3;
    static final int STATE_CONNECTION_FAILED=4;
    static final int STATE_MESSAGE_RECEIVED=5;


    private static final String APP_NAME = "BTChat";
    private static final UUID MY_UUID=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private ArrayList<BluetoothDevice> devicesAvailable ;
    private ArrayList<BluetoothDevice> discoveryDevices ;
    private ActivityMainBinding binding ;
    private final ActivityResultLauncher<Intent> someActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                }
            });

    @Override
    @SuppressLint("MissingPermission")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        checkPermissionApp();


        devicesAvailable = new ArrayList<>();
        discoveryDevices = new ArrayList<>();
        listeningDevice();
        bluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
        if(!bluetoothAdapter.isEnabled())
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            someActivityResultLauncher.launch(enableIntent);
        }



        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();
        implementListeners();
    }

    private void checkPermissionApp() {
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P){
            if (ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{ACCESS_COARSE_LOCATION},
                        1);
            }
        }else {
            if (ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{ACCESS_FINE_LOCATION},
                        2);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this,
                    ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{ACCESS_BACKGROUND_LOCATION},
                        3);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void implementListeners() {

        binding.listDevices.setOnClickListener(view -> {
            devicesAvailable.clear();
            @SuppressLint("MissingPermission")
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
            Set<BluetoothDevice> bt =bluetoothAdapter.getBondedDevices();
            devicesAvailable.addAll(bt);
            devicesAvailable.addAll(discoveryDevices);
            String[] strings=new String[devicesAvailable.size()];
            btArray=new BluetoothDevice[devicesAvailable.size()];
            int index=0;
            if( bt.size()>0)
            {
                for(BluetoothDevice device : devicesAvailable)
                {
                    btArray[index]= device;
                    strings[index]=getNameFor(device);
                    index++;
                }
                ArrayAdapter<String> arrayAdapter=new ArrayAdapter<>(getApplicationContext(),android.R.layout.simple_list_item_1,strings);
                binding.listview.setAdapter(arrayAdapter);

            }
        });

        binding.btnListen.setOnClickListener(view -> {
            bluetoothAdapter.cancelDiscovery();
            ServerClass serverClass=new ServerClass();
            serverClass.start();
        });

        binding.listview.setOnItemClickListener((adapterView, view, i, l) -> {
            bluetoothAdapter.cancelDiscovery();
            ClientClass clientClass=new ClientClass(btArray[i]);
            clientClass.start();

            binding.tvStatus.setText(getResources().getString(R.string.connecting));
        });

        binding.btnSend.setOnClickListener(view -> {
            String string= String.valueOf(binding.edtMessage.getText());

            User user = new User("Toan","Test");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = null;
            try {
                out = new ObjectOutputStream(bos);
                out.writeObject(user);
                out.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    bos.close();
                } catch (IOException ignored) {

                }
            }
            Log.d("TTT",string+ " /"+ Arrays.toString(string.getBytes()));
            sendReceive.write(bos.toByteArray());

        });
    }

    Handler handler=new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {

            switch (msg.what)
            {
                case STATE_LISTENING:
                    binding.tvStatus.setText(getResources().getString(R.string.listening));
                    break;
                case STATE_CONNECTING:
                    binding.tvStatus.setText(getResources().getString(R.string.connecting));
                    break;
                case STATE_CONNECTED:
                    binding.tvStatus.setText(getResources().getString(R.string.connected));
                    break;
                case STATE_CONNECTION_FAILED:
                    binding.tvStatus.setText(getResources().getString(R.string.connection_failed));
                    break;
                case STATE_MESSAGE_RECEIVED:
                    byte[] readBuff= (byte[]) msg.obj;
//                    String tempMsg=new String(readBuff,0,msg.arg1);
                    ByteArrayInputStream bis = new ByteArrayInputStream(readBuff);
                    ObjectInput in = null;
                    User user;
                    try {
                        in = new ObjectInputStream(bis);
                        user = (User) in.readObject();
                    } catch (IOException | ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    } finally {
                        try {
                            if (in != null) {
                                in.close();
                            }
                        } catch (IOException ex) {
                            // ignore close exception
                        }
                    }
//                    msg_box.setText(tempMsg);
                    binding.tvMessage.setText(user.getName());
                    break;
            }
            return true;
        }
    });


    private class ServerClass extends Thread
    {
        private BluetoothServerSocket serverSocket;

        @SuppressLint("MissingPermission")
        public ServerClass(){
            try {
                serverSocket=bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME,MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run()
        {
            BluetoothSocket socket=null;

//            while (socket==null)
//            {
                try {
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTING;
                    handler.sendMessage(message);

                    socket=serverSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTION_FAILED;
                    handler.sendMessage(message);
                }

                if(socket!=null)
                {
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTED;
                    handler.sendMessage(message);

                    sendReceive=new SendReceive(socket);
                    sendReceive.start();


                }
//            }
        }
        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e("TTT", "Could not close the server socket", e);
            }
        }
    }

    private class ClientClass extends Thread
    {
        private BluetoothDevice device;
        private BluetoothSocket socket;

        @SuppressLint("MissingPermission")
        public ClientClass (BluetoothDevice device1)
        {
            device=device1;

            try {
                socket=device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @SuppressLint("MissingPermission")
        public void run()
        {
            try {
                socket.connect();
                Message message=Message.obtain();
                message.what=STATE_CONNECTED;
                handler.sendMessage(message);

                sendReceive=new SendReceive(socket);
                sendReceive.start();

            } catch (IOException e) {
                e.printStackTrace();
                Message message=Message.obtain();
                message.what=STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e("TTT", "Could not close the connect socket", e);
            }
        }
    }

    private class SendReceive extends Thread
    {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public SendReceive (BluetoothSocket socket)
        {
            bluetoothSocket=socket;
            InputStream tempIn=null;
            OutputStream tempOut=null;

            try {
                tempIn=bluetoothSocket.getInputStream();
                tempOut=bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            inputStream=tempIn;
            outputStream=tempOut;
        }

        public void run()
        {
            byte[] buffer=new byte[1024];
            int bytes;

//            while (true)
//            {
                try {
                    bytes=inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED,bytes,-1,buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
//            }
        }

        public void write(byte[] bytes)
        {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.e("TTT", "Could not close the connect socket", e);
            }
        }
    }
    public final BroadcastReceiver receiver = new  BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(!discoveryDevices.contains(device)){
                    discoveryDevices.add(device);
                }
            }
        }
    };

    private void listeningDevice(){
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(receiver, filter);
    }
    @SuppressLint("MissingPermission")
    private String getNameFor(BluetoothDevice device) {
        return ((device.getName() == null || device.getName().equals("null")) ? "Unknown" : device.getName());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }
}
