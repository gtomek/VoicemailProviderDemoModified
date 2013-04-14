/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.voicemail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.voicemail.common.core.Voicemail;
import com.example.android.voicemail.common.core.VoicemailImpl;
import com.example.android.voicemail.common.core.VoicemailProviderHelper;
import com.example.android.voicemail.common.core.VoicemailProviderHelpers;
import com.example.android.voicemail.common.inject.InjectView;
import com.example.android.voicemail.common.inject.Injector;
import com.example.android.voicemail.common.logging.Logger;
import com.example.android.voicemail.common.ui.DialogHelperImpl;
import com.example.android.voicemail.common.utils.CloseUtils;

/**
 * A simple activity that stores user entered voicemail data into voicemail content provider. To be
 * used as a test voicemail source.
 */
public class AddVoicemailActivity extends Activity {
    private static final Logger logger = Logger.getLogger(AddVoicemailActivity.class);

    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("dd/MM/yyyy h:mm");

    private static final int REQUEST_CODE_RECORDING = 100;

    private final DialogHelperImpl mDialogHelper = new DialogHelperImpl(this);
    /**
     * This is created in {@link #onCreate(Bundle)}, and needs to be released in
     * {@link #onDestroy()}.
     */
    private VoicemailProviderHelper mVoicemailProviderHelper;
    private Uri mRecordingUri;

    // Mark the views as injectable. These objects are instantiated automatically during
    // onCreate() by finding the appropriate view that matches the specified resource_id.
    @InjectView(R.id.start_recording_btn)
    private Button mStartRec;
    @InjectView(R.id.save_btn)
    private Button mSaveButton;
    @InjectView(R.id.add_voicemail_button)
    private Button mAddVoicemailNoAttachment;
    @InjectView(R.id.time)
    private TextView mTime;
    @InjectView(R.id.provider_package)
    private TextView mProviderPackage;
    @InjectView(R.id.mime_type)
    private TextView mMimeType;
    @InjectView(R.id.sender_number)
    private TextView mSenderNumber;
    @InjectView(R.id.duration)
    private TextView mDuration;

	private Uri mTemportaryVoicemailUri;

	private boolean mIsRequiredToAddVoicemailAttachment;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_voicemail);
        // Inject all objects that are marked by @InjectView annotation.
        Injector.get(this).inject();
        mVoicemailProviderHelper = VoicemailProviderHelpers.createPackageScopedVoicemailProvider(this);

        setDefaultValues();

        // Record voice button.
        mStartRec.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });

        // Save voicemail button.
        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                storeVoicemail();
            }

        });
        
        // Add voicemail without attachment
        mAddVoicemailNoAttachment.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				storeVoicemailWithoutAttachment();
			}
		});
    }

	private void storeVoicemail() {
		mIsRequiredToAddVoicemailAttachment  = false;
        try {
            Pair<Voicemail, Uri> newVoicemail = new Pair<Voicemail, Uri>(
                    buildVoicemailObjectFromUiElements(), mRecordingUri);
            new InsertVoicemailTask().execute(newVoicemail);
        } catch (ParseException e) {
            handleError(e);
        }
    }
	
    protected void storeVoicemailWithoutAttachment() {
    	try {
            Pair<Voicemail, Uri> newVoicemail = new Pair<Voicemail, Uri>(
                    buildVoicemailObjectFromUiElementsWithoutAttachment(), mRecordingUri);
            new InsertVoicemailWithoutAttachment().execute(newVoicemail);
        } catch (ParseException e) {
            handleError(e);
        }
    	
	}

	private void addVoicemailAttachement(Uri recordingUri, Uri voicemailUri) {
		logger.d("Adding voice attachment");
    	InputStream inputAudioStream = null;
		try {
			inputAudioStream = recordingUri == null ? null : getContentResolver().openInputStream(
					recordingUri);
		} catch (FileNotFoundException e1) {
			displayErrorToast();
			logger.e("recordingUri not found!");
			e1.printStackTrace();
		}
		if (inputAudioStream != null) {
			try {
				try {
					logger.i("Setting voicemail Content");
					mVoicemailProviderHelper.setVoicemailContent(voicemailUri,
							inputAudioStream, getContentResolver().getType(recordingUri));
					displayOKToast();
				} catch (IOException e) {
					displayErrorToast();
					logger.e("setVoicemailContent failed!");
					e.printStackTrace();
				}
			} finally {
				CloseUtils.closeQuietly(inputAudioStream);
			}
		}
	}

    private Voicemail buildVoicemailObjectFromUiElements() throws ParseException {
        String sender = mSenderNumber.getText().toString();
        String dateStr = mTime.getText().toString();
        String durationStr = mDuration.getText().toString();
        String mimeType = mMimeType.getText().toString();
        String sourcePackageName = mProviderPackage.getText().toString();
        long time = DATE_FORMATTER.parse(dateStr.trim()).getTime();
        long duration = durationStr.length() != 0 ? Long.parseLong(durationStr) : 0;
        return VoicemailImpl.createForInsertion(time, sender)
                .setDuration(duration)
                .setSourcePackage(sourcePackageName)
                .build();
    }
    
    // This works in the same way as buildVoicemailObjectFromUiElements() but does not 
    // add the MIME type and voice attachment
    private Voicemail buildVoicemailObjectFromUiElementsWithoutAttachment() throws ParseException {
        String sender = mSenderNumber.getText().toString();
        String dateStr = mTime.getText().toString();
        String durationStr = mDuration.getText().toString();
        String sourcePackageName = mProviderPackage.getText().toString();
        long time = DATE_FORMATTER.parse(dateStr.trim()).getTime();
        long duration = durationStr.length() != 0 ? Long.parseLong(durationStr) : 0;
        return VoicemailImpl.createForInsertion(time, sender)
                .setDuration(duration)
                .setSourcePackage(sourcePackageName)
                .build();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	logger.d(String.format("received requestCode r=%s", requestCode));
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_RECORDING:
                handleRecordingResult(resultCode, data);
                break;
            default:
                logger.e("onActivityResult: Unexpected requestCode: " + requestCode);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        return mDialogHelper.handleOnCreateDialog(id, bundle);
    }

    /** Set default values in the display */
    private void setDefaultValues() {
        // Set time to current time.
        mTime.setText(DATE_FORMATTER.format(new Date()));

        // Set provider package to this app's package.
        mProviderPackage.setText(getPackageName());
    }

    private void startRecording() {
    	logger.d("Starting recording");
        Intent recordingIntent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
        startActivityForResult(recordingIntent, REQUEST_CODE_RECORDING);
    }

    private void handleRecordingResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            handleError(new Exception("Failed to do recording. Error Code: " + resultCode));
            displayErrorToast();
        }

        Uri uri = data.getData();
        logger.d("Received recording URI: " + uri);
        if (uri != null) {
            mRecordingUri = uri;
            mMimeType.setText(getContentResolver().getType(uri));
        }
        
        if (mIsRequiredToAddVoicemailAttachment) {
        	addVoicemailAttachement(mRecordingUri, mTemportaryVoicemailUri);
        }
        
    }

    private void displayErrorToast() {
		Toast.makeText(getApplicationContext(), R.string.error_insertion, Toast.LENGTH_LONG).show();
	}
    
    private void displayOKToast() {
		Toast.makeText(getApplicationContext(), R.string.ok_insertion, Toast.LENGTH_LONG).show();
	}

	private void handleError(Exception e) {
        mDialogHelper.showErrorMessageDialog(R.string.voicemail_store_error, e);
    }

    /**
     * An async task that inserts a new voicemail record using a background thread.
     * The tasks accepts a pair of voicemail object and the recording Uri as the param.
     * The result returned is the error exception, if any, encountered during the operation.
     */
    private class InsertVoicemailTask extends AsyncTask<Pair<Voicemail, Uri>, Void, Exception> {
        @Override
        protected Exception doInBackground(Pair<Voicemail, Uri>... params) {
            if (params.length > 0) {
                try {
                    insertVoicemail(params[0].first, params[0].second);
                } catch (IOException e) {
                    return e;
                }
            }
            return null;
        }

        private void insertVoicemail(Voicemail voicemail, Uri recordingUri) throws IOException {
            InputStream inputAudioStream = recordingUri == null ? null :
                  getContentResolver().openInputStream(recordingUri);
            Uri newVoicemailUri = mVoicemailProviderHelper.insert(voicemail);
            logger.i("Inserted new voicemail URI: " + newVoicemailUri);
            if (inputAudioStream != null) {
                try {
                    mVoicemailProviderHelper.setVoicemailContent(newVoicemailUri, inputAudioStream,
                            getContentResolver().getType(recordingUri));
                } finally {
                    CloseUtils.closeQuietly(inputAudioStream);
                }
            }
        }

        @Override
        protected void onPostExecute(Exception error) {
            if (error == null) {
                // No error - done.
                finish();
            } else {
                handleError(error);
            }
        }

    }
    
    private class InsertVoicemailWithoutAttachment extends AsyncTask<Pair<Voicemail, Uri>, Void, Exception> {
        @Override
        protected Exception doInBackground(Pair<Voicemail, Uri>... params) {
            if (params.length > 0) {
                try {
                    insertVoicemail(params[0].first, params[0].second);
                } catch (IOException e) {
                    return e;
                }
            }
            return null;
        }

        private void insertVoicemail(Voicemail voicemail, Uri recordingUri) throws IOException {
        	mTemportaryVoicemailUri = mVoicemailProviderHelper.insert(voicemail);
            logger.i("Inserted new voicemail URI: " + mTemportaryVoicemailUri);
        }

        @Override
        protected void onPostExecute(Exception error) {
            if (error == null) {
                // No error - done.
                startRecording();
            	mIsRequiredToAddVoicemailAttachment = true;
            } else {
                handleError(error);
            }
        }

    }
}
