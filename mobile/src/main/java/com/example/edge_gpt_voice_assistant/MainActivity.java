package com.example.edge_gpt_voice_assistant;

import androidx.appcompat.app.AppCompatActivity;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Button;

import android.widget.TextView;
import android.widget.Toast;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity {

    private static final int SPEECH_REQUEST_CODE = 0;

    private SpeechRecognizer speechRecognizer;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        textView = findViewById(R.id.prompt_text_view);

        Button startButton = findViewById(R.id.start_button);
        startButton.setOnClickListener(v -> startVoiceRecognitionActivity());
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Release the speech recognizer resources
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }

    private void startVoiceRecognitionActivity() {
        // Create an intent to start the voice recognition activity
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        // Start the activity and wait for a response
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }



    private static class NetworkTask extends AsyncTask<Void, Void, String> {
        private WeakReference<Context> contextRef;
        private String prompt;

        public NetworkTask(Context context, String prompt) {
            contextRef = new WeakReference<>(context);
            this.prompt = prompt;
        }

        @Override
        protected String doInBackground(Void... params) {
            // Set up the API request
            OkHttpClient client = new OkHttpClient();
            MediaType mediaType = MediaType.parse("application/json");

            JSONObject requestObject = new JSONObject();
            try {
                requestObject.put("model", "gpt-3.5-turbo");

                JSONArray messagesArray = new JSONArray();
                JSONObject messageObject = new JSONObject();
                messageObject.put("role", "user");
                messageObject.put("content", prompt);
                messagesArray.put(messageObject);
                requestObject.put("messages", messagesArray);

                requestObject.put("temperature", 0.7);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            RequestBody body = RequestBody.create(mediaType, requestObject.toString());

            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .post(body)
                    .addHeader("Authorization", "Bearer {OPEN-API KEY}")
                    .addHeader("Content-Type", "application/json")
                    .build();

            // Send the API request and retrieve the response
            String generatedText = null;
            try {
                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();
                Log.i("NetworkTask", "API response body: " + responseBody);

                // Parse the response body and extract the generated text
                JSONObject jsonObject = new JSONObject(responseBody);
                JSONArray choicesArray = jsonObject.optJSONArray("choices");

                if (choicesArray != null && choicesArray.length() > 0) {
                    generatedText = jsonObject.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                } else {
                    Log.e("NetworkTask", "Error retrieving GPT-3.5-turbo response: No value for choices");
                }

            } catch (IOException | JSONException e) {
                e.printStackTrace();
                Log.e("NetworkTask", "Error retrieving GPT-3.5-turbo response: " + e.getMessage());
            }

            Log.i("NetworkTask", "Generated text: " + generatedText);
            return generatedText;
        }

        @Override
        protected void onPostExecute(String generatedText) {
            Context context = contextRef.get();
            if (context != null) {
                // Do something with the generated text in the UI thread
                // Update the UI with the generated text
                TextView responseTextView = ((Activity) context).findViewById(R.id.response_text_view);
                responseTextView.setText("GPT-3.5-turbo API response: " + generatedText);

                // Display the recognized speech text and response text in Toast messages
                Toast.makeText(context, "GPT-3.5-turbo API response: " + generatedText, Toast.LENGTH_SHORT).show();
            }
        }
    }


    private void sendPromptAndGetResponse(String prompt) {
        NetworkTask task = new NetworkTask(this, prompt);
        task.execute();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            // Get the recognized speech text
            ArrayList<String> speechTexts = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String speechText = speechTexts.get(0);

            // Send prompt to GPT-3.5-turbo API and receive response
            sendPromptAndGetResponse(speechText);

            // Display the recognized speech text and response text in separate TextViews
            TextView promptTextView = findViewById(R.id.prompt_text_view);
            promptTextView.setText("You said: " + speechText);
        }
    }
}
