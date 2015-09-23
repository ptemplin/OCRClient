package com.petertemplin.ocrclient;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity {

    static final int REQUEST_IMAGE_CAPTURE = 1;

    // conversion factors
    public static final double RED_TO_GRAY_FACTOR = 0.2989;
    public static final double GREEN_TO_GRAY_FACTOR = 0.5878;
    public static final double BLUE_TO_GRAY_FACTOR = 0.1140;

    public ImageView mImageView;
    public Bitmap bitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImageView = (ImageView) findViewById(R.id.imageDisplay);

        ((Button)findViewById(R.id.takePictureButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });

        ((Button)findViewById(R.id.classifyButton)).setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getClassFromServer();
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void takePicture() {
        dispatchTakePictureIntent();
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            bitmap = Bitmap.createScaledBitmap(imageBitmap, 32, 32, false);
            mImageView.setImageBitmap(imageBitmap);
        }
    }

    public void getClassFromServer() {
        StringBuilder image = getPixelsOfBitmap(bitmap);
        GetClassificationTask task = new GetClassificationTask();
        task.url = "http://10.0.0.8:8080/greeting";
        task.image = image;
        task.execute();
    }

    public static String executePost(String targetURL, StringBuilder image) {

        // Create a new HttpClient and Post Header
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost("http://ocr-play-server.herokuapp.com/classify");

        try {
            //Setup body
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            DataOutputStream dos = new DataOutputStream(baos);
//            dos.writeBytes(image.toString());
//            dos.flush();
//            dos.close();
//
//            ByteArrayInputStream content = new ByteArrayInputStream(baos.toByteArray());
//            BasicHttpEntity entity = new BasicHttpEntity();
//            entity.setContent(content);
//            httppost.setEntity(entity);

            BasicNameValuePair imageDataPair = new BasicNameValuePair("data", image.toString());
            List<NameValuePair> nameValuePairList = new ArrayList<NameValuePair>();
            nameValuePairList.add(imageDataPair);
            UrlEncodedFormEntity urlEncodedFormEntity = new UrlEncodedFormEntity(nameValuePairList);

            // setEntity() hands the entity (here it is urlEncodedFormEntity) to the request.
            httppost.setEntity(urlEncodedFormEntity);

            // Execute HTTP Post Request
            HttpResponse response = httpclient.execute(httppost);
            InputStream is = response.getEntity().getContent();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            StringBuilder builder = new StringBuilder(); // or StringBuffer if not Java 5+
            String line;
            while((line = rd.readLine()) != null) {
                builder.append(line);
                builder.append('\r');
            }
            rd.close();
            return builder.toString();

        } catch (IOException e) {
            return "No response / Problem with data";
        }
    }

    public static StringBuilder getPixelsOfBitmap(Bitmap bits) {
        try {
            int[] pixels = getPixels(bits);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < pixels.length; i++) {
                builder.append(Integer.toString(pixels[i]) + " ");
            }
            return builder;
        } catch (NumberFormatException e) {
            System.out.println("Not a usable folder/image");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static int[] getPixels(Bitmap b) throws IOException{

        int[] pixels = new int[b.getHeight()*b.getWidth()];
        b.getPixels(pixels, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        // convert to grayscale
        for (int i = 0; i < pixels.length;i++) {
            pixels[i] = rgbToGrayScale(pixels[i]);
        }

        return pixels;
    }

    public static int rgbToGrayScale(int color) {
        double red = ((color & 0x00ff0000) >> 16) * RED_TO_GRAY_FACTOR;
        double green = ((color & 0x0000ff00) >> 8) * GREEN_TO_GRAY_FACTOR;
        double blue = (color & 0x000000ff) * BLUE_TO_GRAY_FACTOR;
        return (int) (red + green + blue);
    }

    private class GetClassificationTask extends AsyncTask<String, Void, String> {

        public String url;
        public StringBuilder image;

        @Override
        protected String doInBackground(String... urls) {
            return executePost(url, image);
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            ((TextView)findViewById(R.id.classificationText)).setText(result);
        }
    }
}
