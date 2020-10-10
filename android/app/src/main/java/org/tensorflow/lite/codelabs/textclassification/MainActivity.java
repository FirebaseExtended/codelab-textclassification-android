/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.codelabs.textclassification;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions;
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager;
import com.google.firebase.ml.custom.FirebaseCustomRemoteModel;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.io.File;
import java.util.List;

import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.text.nlclassifier.NLClassifier;

/** The main activity to provide interactions with users. */
public class MainActivity extends AppCompatActivity {
  private static final String TAG = "TextClassificationDemo";

  private TextView resultTextView;
  private EditText inputEditText;
  private Handler handler;
  private ScrollView scrollView;
  private FirebaseAnalytics analytics;
  private FirebaseRemoteConfig remoteConfig;
  private NLClassifier textClassifier;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.tfe_tc_activity_main);
    Log.v(TAG, "onCreate");

    handler = new Handler();
    Button classifyButton = findViewById(R.id.predict_button);
    classifyButton.setOnClickListener(
        (View v) -> {
          classify(inputEditText.getText().toString());
        });
    resultTextView = findViewById(R.id.result_text_view);
    inputEditText = findViewById(R.id.input_text);
    scrollView = findViewById(R.id.scroll_view);
    Button yesButton = findViewById(R.id.yes_button);
    analytics = FirebaseAnalytics.getInstance(this);
    remoteConfig = FirebaseRemoteConfig.getInstance();

    yesButton.setOnClickListener(
        (View v) -> {
          analytics.logEvent("correct_inference", null);
        });
  }

  @Override
  protected void onStart() {
    super.onStart();
    Log.v(TAG, "onStart");
    setupTextClassification();
  }

  @Override
  protected void onStop() {
    super.onStop();
    Log.v(TAG, "onStop");
    handler.post(
        () -> {
          textClassifier.close();
        });
  }

  /** Send input text to TextClassificationClient and get the classify messages. */
  private void classify(final String text) {
    handler.post(
        () -> {
          // Run text classification with TF Lite.
          List<Category> results = textClassifier.classify(text);

          // Show classification result on screen
          showResult(text, results);
        });
  }

  /** Show classification result on the screen. */
  private void showResult(final String inputText, final List<Category> results) {
    // Run on UI thread as we'll updating our app UI
    runOnUiThread(
        () -> {
          String textToShow = "Input: " + inputText + "\nOutput:\n";
          for (int i = 0; i < results.size(); i++) {
            Category result = results.get(i);
            textToShow +=
                String.format("    %s: %s\n", result.getLabel(), result.getScore());
          }
          textToShow += "---------\n";

          // Append the result to the UI.
          resultTextView.append(textToShow);

          // Clear the input text.
          inputEditText.getText().clear();

          // Scroll to the bottom to show latest entry's classification result.
          scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
  }

    /** Download model from Firebase ML. */
    private synchronized void downloadModel(String modelName) {
      final FirebaseCustomRemoteModel remoteModel =
          new FirebaseCustomRemoteModel
              .Builder(modelName)
              .build();
      FirebaseModelDownloadConditions conditions =
          new FirebaseModelDownloadConditions.Builder()
              .requireWifi()
              .build();
      final FirebaseModelManager firebaseModelManager = FirebaseModelManager.getInstance();
      firebaseModelManager
          .download(remoteModel, conditions)
          .continueWithTask(task ->
              firebaseModelManager.getLatestModelFile(remoteModel)
          )
          .continueWith((Continuation<File, Void>) task -> {
            // Initialize a text classifier instance with the model
            textClassifier = NLClassifier
                .createFromFile(task.getResult());
            return null;
          })
          .addOnFailureListener(e -> {
            Log.e(TAG, "Failed to build FirebaseModelInterpreter. ", e);
            Toast.makeText(
                MainActivity.this,
                "Model download failed, please check your connection.",
                Toast.LENGTH_LONG)
                .show();
          });
    }

    /** Configure RemoteConfig. */
    private void configureRemoteConfig() {
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build();
        remoteConfig.setConfigSettingsAsync(configSettings);
    }

    /** Setup TextClassification. */
    private void setupTextClassification() {
        configureRemoteConfig();
        remoteConfig.fetchAndActivate().
                addOnCompleteListener(new OnCompleteListener<Boolean>() {
                    @Override
                    public void onComplete(@NonNull Task<Boolean> task) {
                        if (task.isSuccessful()) {
                            final String modelName = remoteConfig.getString("model_name");
                            downloadModel(modelName);
                        } else {
                            Log.d(TAG, "Fetch failed");
                        }
                    }
                });
    }
}
