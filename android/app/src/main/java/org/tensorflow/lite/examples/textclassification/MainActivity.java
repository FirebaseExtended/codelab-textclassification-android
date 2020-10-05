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

package org.tensorflow.lite.examples.textclassification;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions;
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager;
import com.google.firebase.ml.custom.FirebaseCustomRemoteModel;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.io.File;
import java.util.List;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.examples.textclassification.TextClassificationClient.Result;

/** The main activity to provide interactions with users. */
public class MainActivity extends AppCompatActivity {
  private static final String TAG = "TextClassificationDemo";
  private TextClassificationClient client;

  private TextView resultTextView;
  private EditText inputEditText;
  private Handler handler;
  private ScrollView scrollView;
  private FirebaseAnalytics mFirebaseAnalytics;
  private FirebaseRemoteConfig mFirebaseRemoteConfig;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.tfe_tc_activity_main);
    Log.v(TAG, "onCreate");

    client = new TextClassificationClient(getApplicationContext());
    handler = new Handler();
    Button classifyButton = findViewById(R.id.button);
    classifyButton.setOnClickListener(
        (View v) -> {
          classify(inputEditText.getText().toString());
        });
    resultTextView = findViewById(R.id.result_text_view);
    inputEditText = findViewById(R.id.input_text);
    scrollView = findViewById(R.id.scroll_view);
    Button yesButton = findViewById(R.id.yes_button)
    mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
    mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

    yesButton.setOnClickListener(
        (View v) -> {
          mFirebaseAnalytics.logEvent("correct_inference", null);
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
          client.unload();
        });
  }

  /** Send input text to TextClassificationClient and get the classify messages. */
  private void classify(final String text) {
    handler.post(
        () -> {
          // Run text classification with TF Lite.
          List<Result> results = client.classify(text);

          // Show classification result on screen
          showResult(text, results);
        });
  }

  /** Show classification result on the screen. */
  private void showResult(final String inputText, final List<Result> results) {
    // Run on UI thread as we'll updating our app UI
    runOnUiThread(
        () -> {
          String textToShow = "Input: " + inputText + "\nOutput:\n";
          for (int i = 0; i < results.size(); i++) {
            Result result = results.get(i);
            textToShow +=
                String.format("    %s: %s\n", result.getTitle(), result.getConfidence());
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
    private void downloadModel(final String modelName) {
        final FirebaseCustomRemoteModel remoteModel =
                new FirebaseCustomRemoteModel.Builder(modelName).build();
        final FirebaseModelManager firebaseModelManager = FirebaseModelManager.getInstance();
        Trace downloadModelTrace = FirebasePerformance.getInstance().newTrace("download_model");
        firebaseModelManager
                .isModelDownloaded(remoteModel)
                .continueWithTask(
                        new Continuation<Boolean, Task<Void>>() {
                            @Override
                            public Task<Void> then(@NonNull Task<Boolean> task) throws Exception {
                                // Create update condition if model is already downloaded,
                                // otherwise create download
                                // condition.
                                FirebaseModelDownloadConditions conditions =
                                        task.getResult()
                                                ? new FirebaseModelDownloadConditions.Builder()
                                                .requireWifi()
                                                .build() // Update condition that requires wifi.
                                                : new FirebaseModelDownloadConditions.Builder()
                                                .build(); // Download condition.
                                downloadModelTrace.start();
                                return firebaseModelManager.download(remoteModel, conditions);
                            }
                        })
                .addOnSuccessListener(
                        new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void ignored) {
                                downloadModelTrace.stop();
                                firebaseModelManager.getLatestModelFile(remoteModel)
                                        .addOnCompleteListener(new OnCompleteListener<File>() {
                                            @Override
                                            public void onComplete(Task<File> modelFileTask) {
                                                File modelFile = modelFileTask.getResult();
                                                if (modelFile != null) {
                                                    handler.post(
                                                            () -> {
                                                                client.load(modelFile);
                                                            });
                                                }
                                            }
                                        });
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception ignored) {
                                downloadModelTrace.stop();
                                Log.e(TAG, "Failed to build FirebaseModelInterpreter. ", ignored);
                                Toast.makeText(
                                        context,
                                        "Model download failed, please check" +
                                                " your connection.",
                                        Toast.LENGTH_LONG)
                                        .show();
                            }
                        });
    }

    /** Configure RemoteConfig. */
    private void configureRemoteConfig() {
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build();
        mFirebaseRemoteConfig.setConfigSettingsAsync(configSettings);
    }

    /** Setup TextClassification. */
    private void setupTextClassification() {
        configureRemoteConfig();
        mFirebaseRemoteConfig.fetchAndActivate().
                addOnCompleteListener(new OnCompleteListener<Boolean>() {
                    @Override
                    public void onComplete(@NonNull Task<Boolean> task) {
                        if (task.isSuccessful()) {
                            final String modelName = mFirebaseRemoteConfig.getString("model_name");
                            downloadModel(modelName);
                        } else {
                            Log.d(TAG, "Fetch failed");
                        }
                    }
                });
    }
}
