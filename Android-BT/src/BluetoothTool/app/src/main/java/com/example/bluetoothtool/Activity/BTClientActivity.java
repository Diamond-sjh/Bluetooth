package com.example.bluetoothtool.Activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.example.bluetoothtool.R;
import com.example.bluetoothtool.common.GlobalDef;
import com.example.bluetoothtool.common.LoadingDialog;
import com.example.bluetoothtool.common.ScanListAdapter;
import com.example.bluetoothtool.utils.BluetoothUtil;
import com.example.bluetoothtool.utils.FileDigest;
import com.example.bluetoothtool.utils.StringUtil;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class BTClientActivity extends BaseActivity{
    private static final String TAG="BTClientLog";

    private int[] mBtnArray=new int[]{
            R.id.btnOpenBt,R.id.btnCloseBt,R.id.btnOpenDiscovery,R.id.btnCloseDiscovery,
            R.id.btnScanBt,R.id.btnGetBondList,R.id.btnDisconnect,R.id.btnClearTip,
            R.id.btnSendData,R.id.btnSelectFile,R.id.btnSendFile
    };
    private RadioGroup rgDataType;
    private EditText edtData;
    private TextView txtFilePath;

    //???????????????????????????
    private AlertDialog scanDialog=null;
    private ScanListAdapter listAdapter=null;
    private List<BluetoothDevice> deviceList=new LinkedList<>();
    private List<Short> rssiList=new LinkedList<>();

    private BluetoothBroadcastReceiver mBluetoothBroadcastReceiver;
    private BluetoothDevice mCurDevice=null;
    private int mFlag=0;//0:??????????????????1??????????????????-1???????????????
    private SocketThread mThread=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);

        mContext=this;
        mBluetoothAdapter= BluetoothAdapter.getDefaultAdapter();
        initView();
        loadingDialog=new LoadingDialog(mContext);
        //????????????
        mBluetoothBroadcastReceiver=new BluetoothBroadcastReceiver();
        IntentFilter filter=new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBluetoothBroadcastReceiver,filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mContext.unregisterReceiver(mBluetoothBroadcastReceiver);
    }

    public void initView(){
        //?????????
        txtTip=(TextView)findViewById(R.id.txtTip);
        txtTip.setText("");
        txtTip.setMovementMethod(ScrollingMovementMethod.getInstance());
        txtFilePath=(TextView)findViewById(R.id.txtFilePath);
        txtFilePath.setText("");
        edtData=(EditText)findViewById(R.id.edtData);
        edtData.setText("");

        rgDataType=(RadioGroup)findViewById(R.id.rgDataType);
        rgDataType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId){
                    case R.id.rBtnStr:{
                        mCurDataType=DATATYPE_STR;
                        break;
                    }
                    case R.id.rBtnHex:{
                        mCurDataType=DATATYPE_HEX;
                        break;
                    }
                }
            }
        });
        rgDataType.check(R.id.rBtnStr);

        for(int tempId : mBtnArray){
            Button btnTemp=(Button)findViewById(tempId);
            btnTemp.setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btnOpenBt:{
                openBluetooth();
                break;
            }
            case R.id.btnCloseBt:{
                closeBluetooth();
                break;
            }
            case R.id.btnOpenDiscovery:{
                openDiscovery();
                break;
            }
            case R.id.btnCloseDiscovery:{
                closeDiscovery();
                break;
            }
            case R.id.btnClearTip:{
                cleanTip();
                break;
            }
            case R.id.btnScanBt:{
                scanBluetooth();
                break;
            }
            case R.id.btnGetBondList:{
                getBondList();
                break;
            }
            case R.id.btnDisconnect:{
                disconnect();
                break;
            }
            case R.id.btnSendData:{
                sendData();
                break;
            }
            case R.id.btnSelectFile:{
                openFileSelector();
                break;
            }
            case R.id.btnSendFile:{
                sendFileData();
                break;
            }
        }
    }

    /**
     * ??????????????????
     */
    public void scanBluetooth(){
        if(mBluetoothAdapter==null){
            showTip("????????????????????????????????????");
            return;
        }
        if(!mBluetoothAdapter.isEnabled()){
            showTip("?????????????????????");
            return;
        }

        if(mThread!=null && mThread.isConnected()){
            showTip("??????socket??????????????????????????????????????????");
            return;
        }

        showScanDialog();
    }

    /**
     * ???????????????????????????????????????
     */
    private void showScanDialog(){
        LayoutInflater layoutInflater=LayoutInflater.from(mContext);
        View mLayout=layoutInflater.inflate(R.layout.dialog_scan,null);

        //???????????????
        ListView listScan=(ListView)mLayout.findViewById(R.id.listScan);
        deviceList.clear();
        rssiList.clear();
        listAdapter=new ScanListAdapter(deviceList,rssiList,mContext);
        listScan.setAdapter(listAdapter);
        listScan.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                mCurDevice=deviceList.get(i);
                new Thread(){
                    @Override
                    public void run(){
                        bondAndConnect();
                    }
                }.start();
                scanDialog.dismiss();
            }
        });

        AlertDialog.Builder builder=new AlertDialog.Builder(mContext)
                .setView(mLayout)
                .setCancelable(true);
        scanDialog=builder.create();
        scanDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                Log.d(TAG,"scanDialog dismiss");
                if(mBluetoothAdapter.isDiscovering()){
                    mBluetoothAdapter.cancelDiscovery();
                }
            }
        });
        //????????????
        if(mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
        }
        mBluetoothAdapter.startDiscovery();
        //???????????????
        scanDialog.show();

        Window window = scanDialog.getWindow();
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        window.setLayout(width-80,height*3/4);
    }

    /**
     * ????????????????????????
     */
    public void getBondList(){
        if(mBluetoothAdapter==null){
            showTip("????????????????????????????????????");
            return;
        }
        if(!mBluetoothAdapter.isEnabled()){
            showTip("?????????????????????");
            return;
        }

        if(mThread!=null && mThread.isConnected()){
            showTip("??????socket??????????????????????????????????????????");
            return;
        }

        Set<BluetoothDevice> bondList=mBluetoothAdapter.getBondedDevices();
        final List<BluetoothDevice> list = new ArrayList<>(bondList);
        String[] deviceNameList=new String[list.size()];
        for(int i=0;i<list.size();i++){
            deviceNameList[i]=list.get(i).getName();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        AlertDialog dialog= builder.setTitle("????????????????????????????????????")
                .setSingleChoiceItems(deviceNameList, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mCurDevice=list.get(which);
                        new Thread(){
                            @Override
                            public void run(){
                                bondAndConnect();
                            }
                        }.start();
                        dialog.dismiss();
                    }
                }).create();
        dialog.show();
    }

    /**
     * ?????????????????????
     */
    public void bondAndConnect(){
        //????????????
        if(mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
        }

        if(mCurDevice==null){
            showTip("???????????????????????????");
            return;
        }

        //????????????????????????????????????????????????
        if(mCurDevice.getBondState()==BluetoothDevice.BOND_NONE){
            Log.d(TAG,"create bond to "+mCurDevice.getName());
            boolean nRet= BluetoothUtil.createBond(mCurDevice);
            if(!nRet){
                showTip("createBond fail???");
                return;
            }
            showLoadingDialog("????????????"+mCurDevice.getName()+"???????????????...");
            mFlag=0;
            while(mFlag==0){
                SystemClock.sleep(250);
            }
            if(mFlag==-1){
                showTip("??????"+mCurDevice.getName()+"????????????????????????");
                dismissLoadingDialog();
                return;
            }
        }

        if(mCurDevice.getBondState()==BluetoothDevice.BOND_BONDED){
            showLoadingDialog("????????????"+mCurDevice.getName()+"???????????????...");
            try {
                //??????Socket
                BluetoothSocket socket = mCurDevice.createRfcommSocketToServiceRecord(GlobalDef.BT_UUID);
                //??????????????????
                socket.connect();
                mThread=new SocketThread(socket);
                mThread.start();
                showTip(("????????????"+mCurDevice.getName()+"???????????????"));
            } catch (IOException e) {
                Log.d(TAG,"socket connect fail");
                showTip(("?????????"+mCurDevice.getName()+"?????????"));
                e.printStackTrace();
            }
        }
        dismissLoadingDialog();
    }

    /**
     * ????????????
     */
    public void disconnect(){
        if(mThread==null || mThread.isConnected()==false){
            showTip("?????????????????????");
            return;
        }

        try{
            if(mThread!=null){
                mThread.release();
                mThread=null;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * ??????????????????????????????????????????
     */
    public void sendData(){
        if(mThread==null || mThread.isConnected()==false){
            showTip("??????socket?????????");
            return;
        }

        final String strData=edtData.getText().toString();
        if(strData==null || strData.trim().equals("")){
            showTip("?????????????????????!");
            return;
        }

        try{
            byte[] bData=null;
            if(mCurDataType==DATATYPE_STR){
                bData=strData.getBytes();
            }
            else{
                bData= StringUtil.hexStringToBytes(strData);
            }

            if(bData==null){
                showTip("????????????????????????????????????!");
                return;
            }

            if(mThread!=null){
                mThread.writeData(bData,0,bData.length);
                showTip("??????????????????");
            }
        }catch (Exception e){
            e.printStackTrace();
            showTip("????????????????????????!");
        }
    }

    /**
     * ????????????
     */
    public void sendFileData(){
        if(mThread==null || mThread.isConnected()==false){
            showTip("??????socket?????????");
            return;
        }

        final String strPath=txtFilePath.getText().toString();
        if(strPath==null || strPath.trim().equals("")){
            showTip("??????????????????????????????");
            return;
        }

        new Thread(){
            @Override
            public void run(){
                try{
                    File nFile=new File(strPath);
                    if(!nFile.exists()){
                        showTip("???????????????????????????");
                        return;
                    }

                    if(!nFile.isFile()){
                        showTip("??????????????????????????????");
                        return;
                    }

                    if(mThread!=null){
                        showLoadingDialog("?????????????????????????????????...");

                        JSONObject infoJson=new JSONObject();
                        infoJson.put("FileName",nFile.getName());
                        infoJson.put("FileSize",nFile.length());
                        infoJson.put("MD5", FileDigest.getFileMD5(nFile));
                        byte[] tmpJsonBytes=infoJson.toString().getBytes();
                        byte[] tmpInfo=new byte[tmpJsonBytes.length+2];
                        tmpInfo[0]=0x01;
                        System.arraycopy(tmpJsonBytes,0,tmpInfo,1,tmpJsonBytes.length);
                        tmpInfo[tmpInfo.length-1]=0x04;
                        mThread.writeData(tmpInfo,0,tmpInfo.length);

                        byte[] mFileBuffer=new byte[4096];
                        int mFilePos=0;

                        long totalSize=nFile.length();//???????????????
                        long nCurSize=0;//???????????????
                        int nRate=0;//?????????????????????

                        showLoadingDialog("????????????????????????..."+nRate+"%");
                        FileInputStream fIn=new FileInputStream(nFile);
                        while(true){
                            Arrays.fill(mFileBuffer,(byte)0x00);
                            mFilePos=fIn.read(mFileBuffer);
                            if(mFilePos>0){
                                mThread.writeData(mFileBuffer,0,mFilePos);
                                nCurSize+=mFilePos;
                                //?????????????????????
                                int tmpRate=getPercent(nCurSize,totalSize);
                                if(tmpRate!=nRate){
                                    nRate=tmpRate;
                                    showLoadingDialog("????????????????????????..."+nRate+"%");
                                }
                            }
                            else{
                                break;
                            }
                            //?????????????????????????????????
                            SystemClock.sleep(25);
                        }
                        fIn.close();

                        dismissLoadingDialog();
                        showTip("??????????????????");
                    }
                }catch (Exception e){
                    e.printStackTrace();
                    showTip("????????????????????????!");
                    dismissLoadingDialog();
                }

            }
        }.start();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == Activity.RESULT_OK && requestCode==GlobalDef.REQ_CODE_SELECT_FILE){
            Uri uri = data.getData();
            String strPath=convertToRealPath(getPath(mContext,uri));
            txtFilePath.setText(strPath);
        }
    }

    class SocketThread extends Thread {
        private BluetoothSocket mSocket=null;
        private InputStream mIn;
        private OutputStream mOut;
        private boolean isOpen = false;
        private byte[] mRecBuffer=new byte[1024*10];
        private int mRecPos=0;

        public SocketThread(BluetoothSocket socket) {
            try {
                mSocket=socket;
                mIn = mSocket.getInputStream();
                mOut = mSocket.getOutputStream();
                isOpen = true;
                Log.d(TAG, "a socket thread create");
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "create SocketThread fail");
            }
        }

        @Override
        public void run() {
            int readLen=0;
            byte[] buffer=new byte[1024];
            try{
                while(isOpen){
                    readLen=mIn.read(buffer);
                    if(readLen>0){
                        System.arraycopy(buffer,0,mRecBuffer,mRecPos,readLen);
                        mRecPos+=readLen;

                        while(true){
                            if(mIn.available()>0){//??????????????????????????????
                                readLen=mIn.read(buffer);
                                if((mRecPos+readLen)>mRecBuffer.length){//???????????????
                                    showTip("????????????????????????????????????????????????????????????");
                                    showTip("Receive hex data = "+StringUtil.bytesToHexString(mRecBuffer,0,mRecPos));
                                    showTip("Receive string data = "+new String(mRecBuffer,0,mRecPos).trim());
                                    mRecPos=0;
                                    Arrays.fill(mRecBuffer,(byte)0x00);
                                }

                                System.arraycopy(buffer,0,mRecBuffer,mRecPos,readLen);
                                mRecPos+=readLen;
                            }
                            else{//??????????????????
                                if(mRecPos>0){//????????????????????????????????????????????????
                                    showTip("Receive hex data = "+StringUtil.bytesToHexString(mRecBuffer,0,mRecPos));
                                    showTip("Receive string data = "+new String(mRecBuffer,0,mRecPos).trim());
                                    mRecPos=0;
                                    Arrays.fill(mRecBuffer,(byte)0x00);
                                }
                                break;
                            }
                        }
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
                release();
            }
        }

        public boolean isConnected(){
            if(isOpen && mSocket!=null){
                return true;
            }
            return false;
        }

        public void release(){
            Log.d(TAG,"A socketThread release");
            try{
                if(isOpen){
                    showTip("????????????????????????????????????");
                }
                isOpen=false;

                if(mOut!=null){
                    try{
                        mOut.close();
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    mOut=null;
                }
                if(mIn!=null){
                    try{
                        mIn.close();
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    mIn=null;
                }
                if(mSocket!=null){
                    try{
                        mSocket.close();
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                    mSocket=null;
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        /**
         * ????????????
         * @param data
         * @param offset
         * @param len
         */
        public void writeData(byte[] data,int offset,int len){
            if (data == null || offset<0 || len<=0 || (len+offset)>data.length) {
                Log.e(TAG,"BT writeData params fail");
                System.out.println(data);

                return;
            }

            try {
                byte[] buffer=new byte[1024];
                int nPos=offset;
                while((nPos-offset)<len){
                    Arrays.fill(buffer,(byte)0x00);
                    System.out.println(buffer);
                    if((len+offset-nPos)>=buffer.length){
                        System.arraycopy(data,nPos,buffer,0,buffer.length);
                        mOut.write(buffer);
                        nPos+=buffer.length;
                    }
                    else{
                        int last=len+offset-nPos;
                        System.arraycopy(data,nPos,buffer,0,last);

                        mOut.write(buffer,0,last);
                        nPos+=last;
                    }
                }
                mOut.flush();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }



    class BluetoothBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent){
            String action=intent.getAction();
            Log.d(TAG,"Action received is "+action);
            //????????????
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice scanDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(scanDevice == null || scanDevice.getName() == null){
                    return;
                }

                int btType=scanDevice.getType();
                if(btType==BluetoothDevice.DEVICE_TYPE_LE || btType==BluetoothDevice.DEVICE_TYPE_UNKNOWN){
                    return;
                }

                Log.d(TAG, "bt name="+scanDevice.getName()+" address="+scanDevice.getAddress());
                deviceList.add(scanDevice);
                short rssi=intent.getExtras().getShort(BluetoothDevice.EXTRA_RSSI);
                rssiList.add(rssi);
                listAdapter.notifyDataSetChanged();
            }
            //????????????
            else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(mCurDevice!=null && btDevice.getAddress().equals(mCurDevice.getAddress())){
                    int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                    if(state==BluetoothDevice.BOND_NONE){
                        showTip("??????????????????" + btDevice.getName() + "?????????");
                        mFlag=-1;
                    }
                    else if(state==BluetoothDevice.BOND_BONDED){
                        showTip("?????????" + btDevice.getName() + "????????????");
                        mFlag=1;
                    }
                }
            }
            else if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                switch (blueState) {
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.i(TAG,"onReceive---------STATE_TURNING_ON");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.i(TAG,"onReceive---------STATE_ON");
                        showTip("?????????????????????ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.i(TAG,"onReceive---------STATE_TURNING_OFF");
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        Log.i(TAG,"onReceive---------STATE_OFF");
                        showTip("?????????????????????OFF");
                        break;
                }
            }
        }
    }

    /**
     * ????????????????????????????????????
     */
    public void openFileSelector(){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");//???????????????????????????????????????????????????????????????????????????
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent,GlobalDef.REQ_CODE_SELECT_FILE);
    }

    public String getPath(final Context context, final Uri uri) {
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                //Log.i(TAG,"isDownloadsDocument***"+uri.toString());
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                //Log.i(TAG,"isMediaDocument***"+uri.toString());
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            //Log.i(TAG,"content***"+uri.toString());
            return getDataColumn(context, uri, null, null);
        }
        //File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            //Log.i(TAG,"file***"+uri.toString());
            return uri.getPath();
        }
        return null;
    }

    public String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    public boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    //?????????SD?????????
    public String convertToRealPath(String path){
        String[] dataStr = path.split("/");
        String fileTruePath = "/sdcard";
        for(int i=4;i<dataStr.length;i++){
            fileTruePath = fileTruePath+"/"+dataStr[i];
        }
        return fileTruePath;
    }

}
