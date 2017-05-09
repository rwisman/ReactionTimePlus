/*
 *     Copyright (C) 2017  Raymond Wisman
 * 			Indiana University SE
 * 			April 7, 2017
 *
 * 	ReactionTime records, displays, and saves reaction times as measured by user generated sound.

	The application is designed for use in science education experiments that
		measure the reaction time to generate a sound near the device.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details <http://www.gnu.org/licenses/>.

 */

package edu.ius.rwisman.reactiontime;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;

import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v7.app.AppCompatActivity;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import android.graphics.Color;
import android.support.v7.widget.Toolbar;
import android.widget.ViewFlipper;

public class ReactionTimeActivityPlus extends AppCompatActivity {

	private static int RECORDER_SAMPLERATE = 44100;
	private static int FREQUENCY=RECORDER_SAMPLERATE/5;
	private final static int WAVES = 400;
	private final static double VOLUME = 1.0;
	private final static int MAX_N = 1000;
    private final static int YELLOW = Color.argb(255, 255, 255, 0);
    private final static int RED = Color.argb(255, 255, 0, 0);

    private final static byte LF = 0;
    private final static byte RF = 1;
    private final static byte LH = 2;
    private final static byte RH = 3;

    private static final String REACTIONTIME_FOLDER = "ReactionTime";
    private static final String REACTIONTIME_FILE_EXT_CSV = ".csv";
    private static final int STARTSTATE = 0;
    private static final int WAITINGSTATE = 1;    
    private static final int REACTSTATE = 2;
    private static final int MAXLEVEL=Short.MAX_VALUE;

	private AudioRecord mRecorder=null;
	private short[] mBuffer;

	private String reactLabel = null;
	private String waitLabel = null;
	private String noUserReactionLabel;
	private String reactionTimeLabel;
	private TextView mReactionTime;

	private File mRecording;
	private double reactionTime=0.0;
    private Button startbutton;
    private Button reactbutton;
	private Activity activity = this;
    private ImageView waitimage;

    private int N;                                                              // number of times test repeated

	private boolean repeating = false;
    private boolean exit = false;
    private boolean done = false;
    private boolean showList = false;
    private boolean mIsRecording = false;
    private boolean started = false;
    private boolean vibrate = false;

    private boolean DEBUG = true;

    private boolean [] handfoot = {false, false, false, false};

    private double[] results;
    private byte [] handfootLR;
    private int bufferSize;
    private int currentState = STARTSTATE;

    private int soundLevel = 20000;                                             // Default sound level to detect start/end of test
    private  int delaytimer = 3000;                                             // Default delay timer before starting reponse trial
    private static int[] mSampleRates = new int[] { 44100, 22050, 11025, 8000 };

    Toolbar toolbar;
    ListView listView;
    ViewFlipper viewFlipper;
    Vibrator vibrator;
    AboutDialog about=null;
    AlertDialog alertDialog=null;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.main);

        initRecorder();

        AudioSineWave.initialize(activity,RECORDER_SAMPLERATE,2000,4,1,10);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        listView = (ListView) findViewById(R.id.listview);
        viewFlipper = (ViewFlipper) findViewById(R.id.view_flipper);

        reactLabel = (String) getString(R.string.react_label);
        waitLabel = (String) getString(R.string.wait_label);
        noUserReactionLabel = (String) getString(R.string.nouserreaction_label);
        reactionTimeLabel = (String) getString(R.string.reactiontime_label);
        waitimage = (ImageView) findViewById(R.id.waitimage);

        if (savedInstanceState != null) {
            reactionTime = savedInstanceState.getDouble("reactionTime", 0.0);
            results = savedInstanceState.getDoubleArray("results");
            N = savedInstanceState.getInt("N");
            currentState = savedInstanceState.getInt("currentState");
            repeating = savedInstanceState.getBoolean("repeating");
            started = savedInstanceState.getBoolean("started");
            showList = savedInstanceState.getBoolean("showList");
            soundLevel = savedInstanceState.getInt("soundLevel");
            vibrate = savedInstanceState.getBoolean("vibrate");
            delaytimer = savedInstanceState.getInt("delaytimer");
            handfoot = savedInstanceState.getBooleanArray("handfoot");
            handfootLR = savedInstanceState.getByteArray("handfootLR");
        } else {
            results = new double[MAX_N];
            handfootLR = new byte[MAX_N];
            N = 0;
        }

        startbutton = (Button) findViewById(R.id.startbutton);
        reactbutton = (Button) findViewById(R.id.reactbutton);

        mReactionTime = (TextView) findViewById(R.id.responseTime);

        if (N > 0 && showList)                                                               // Showing repeating list of reaction times
            showList();

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        if (reactionTime > 0.0)                                                              // Show only the one reaction time
            mReactionTime.setText(N + ") " + reactionTimeLabel + ": " + String.format("%.4f", reactionTime) + "s");

        startbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {

                if (currentState != STARTSTATE) return;

                done = !repeating;
                exit = false;
                showList = false;
                currentState = WAITINGSTATE;

                toggleButtonVisibility(currentState);

                new Thread(new Runnable() {
                    @Override
                    public void run() {

                        boolean found = false;
                        int n = 0;
                        int amp = 0;
                        double x=0;

                        do {
                            found = false;
                            n = 0;
                            amp = 0;

                            byte[] data = new byte[mBuffer.length * 2];

                            DataOutputStream output = null;
                            if(DEBUG)
                                try {
                                output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(getFile("raw"))));
                                } catch (IOException e) {}

                            handfootLR[N] = setsignalImage(handfoot);
/*
                            try {
                                Thread.sleep(1000);                                                 // Minimum delay time
                            } catch (Exception e) {
                            }
*/
                            mIsRecording = true;

                            if(exit || mRecorder == null) {
                                // System.out.println("run mRecorder.startRecording();");
                                done = true;
                                break;
                            }

                            AudioSineWave.start();

                            mRecorder.startRecording();

                            n=0;
                            while(n<RECORDER_SAMPLERATE) {                              // 1s delay while ignoring input, allows push button time to open
                                if(exit) break;
                                if(mRecorder == null) {
                                    done = true;
                                    break;
                                }
                                int readSize = mRecorder.read(data, 0, data.length);

                                n = n + readSize;

                                if (DEBUG)
                                    try {
                                        output.write(data, 0, readSize);
                                    } catch (IOException e) {}
                            }

                            setReactionTimeLabel("");

                            boolean earlyreaction = false;
                            int samplelimit = (int)((new Random().nextDouble() * (delaytimer/1000))*RECORDER_SAMPLERATE);

                            n=0;
                            while(n<samplelimit && !earlyreaction) {                                // Random delay, check for early response
                                if(exit) break;
                                if(mRecorder == null) {
                                    done = true;
                                    break;
                                }
                                int readSize = mRecorder.read(data, 0, data.length);

                                for (int i = 0; i < readSize && !earlyreaction && n<samplelimit; i = i + 2) {
                                    x = (double) ((data[i + 1] << 8) | data[i]);

                                    if (x > soundLevel)                                             // Detect early reaction
                                        earlyreaction = true;
                                    n++;
                                }

                                if (DEBUG)
                                    try {
                                        output.write(data, 0, readSize);
                                    } catch (IOException e) {}
                            }

                            if(earlyreaction)
                                setReactionTimeLabel(getString(R.string.toosoon));
                            else {

                                n = 0;                                                              // Start counting # of samples before response

                                if (exit || mRecorder == null) {
                                    done = true;
                                    break;
                                }

                                currentState = REACTSTATE;

                                toggleButtonVisibility(currentState);
                                if (vibrate) vibrator.vibrate(100);

                                while (mIsRecording && !found) {
                                    if (exit || mRecorder == null) {
                                        done = true;
                                        break;
                                    }
                                    int readSize = mRecorder.read(data, 0, data.length);

                                    if (DEBUG)
                                        try {
                                            output.write(data, 0, readSize);
                                        } catch (IOException e) {}

                                    for (int i = 0; i < readSize && !found && n < RECORDER_SAMPLERATE * 2; i = i + 2) {
                                        amp = (int) ((data[i + 1] << 8) | data[i]);

                                        found = amp > soundLevel;

                                        n = n + 1;
                                    }
                                    if (n >= RECORDER_SAMPLERATE * 2)                   // No user reaction in 2 seconds
                                        mIsRecording = false;
                                }

                                if (exit || mRecorder == null) {
                                    done = true;
                                    break;
                                }
                                mRecorder.stop();

                                AudioSineWave.stop();

                                //System.out.println("Reaction time: "+n+"/"+RECORDER_SAMPLERATE+" :" + (n / (double) RECORDER_SAMPLERATE) + "s");

                                reactionTime = n / (double) RECORDER_SAMPLERATE;
                                if (found) {
                                    results[N] = reactionTime;
                                    N++;
                                    setReactionTimeLabel(N + ") " + reactionTimeLabel + ": " + String.format("%.4f", reactionTime) + "s");
                                } else
                                    setReactionTimeLabel(noUserReactionLabel);
                            }

                            if (DEBUG) {
                                if (output != null) {
                                    try {
                                        output.flush();
                                    } catch (IOException e) {
                                    } finally {
                                        try {
                                            output.close();
                                        } catch (IOException e) {
                                        }
                                    }
                                }
                                try {
                                    rawToWave(getFile("raw"), getFile("wav"));
                                } catch (Exception e) {
                                }
                            }

                            currentState = WAITINGSTATE;
                            setsignalImage(null);                              // Show outline only
                            toggleButtonVisibility(currentState);
                        } while (!done && N < MAX_N);

                        currentState = STARTSTATE;
                        setsignalImage(null);                              // Show outline only
                        toggleButtonVisibility(currentState);

                        mIsRecording = false;
                        if (!exit && mRecorder != null) mRecorder.stop();
                        exit = false;
                    }
                }).start();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putDouble("reactionTime", reactionTime);
        outState.putDoubleArray("results", results);
        outState.putInt("N", N);
        outState.putBoolean("repeating", repeating);
        outState.putBoolean("mIsRecording", mIsRecording);
        outState.putBoolean("showList", showList);
        outState.putBoolean("started", started);
        outState.putInt("currentState", currentState);
        outState.putInt("soundLevel", soundLevel);
        outState.putBoolean("vibrate", vibrate);
        outState.putInt("delaytimer", delaytimer);
        outState.putBooleanArray("handfoot", handfoot);
        outState.putByteArray("handfootLR", handfootLR);
    }

    @Override
    public void onDestroy() {
        if(mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();

            mRecorder = null;
        }

        exit = true;
        super.onDestroy();
    }

    @Override
    public void onPause() {
        currentState = STARTSTATE;
        exit = true;
        if(about != null)
            about.dismiss();
        if(alertDialog != null)
            alertDialog.dismiss();
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if(viewFlipper.getCurrentView().getId() != R.id.startlayout){
                viewFlipper.showPrevious();
                showList = false;
                return true;
            }
         }
        return true;                                                // Do not exit app on backspace
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
        menu.findItem(R.id.action_vibrate).setChecked(vibrate);
        if(repeating) {
            menu.findItem(R.id.action_repeat).setIcon(getResources().getDrawable(R.drawable.ic_action_repeat_one));
            menu.findItem(R.id.action_repeat).setTitle(R.string.onetime);
        }
        else {
            menu.findItem(R.id.action_repeat).setIcon(getResources().getDrawable(R.drawable.ic_action_repeat));
            menu.findItem(R.id.action_repeat).setTitle(R.string.repeat);
        }
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		int id = item.getItemId();

        if (id == R.id.action_delete) {

            LayoutInflater li = LayoutInflater.from(activity);
            View promptsView = li.inflate(R.layout.delete, null);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);

            alertDialogBuilder.setView(promptsView);

            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    N = 0;
                                    Toast.makeText(ReactionTimeActivityPlus.this, getString(R.string.deleted), Toast.LENGTH_SHORT).show();
                                }
                            })
                    .setNegativeButton(getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    dialog.cancel();
                                }
                            });

            alertDialog = alertDialogBuilder.create();
            alertDialog.show();
            return true;
        }

        if (id == R.id.action_repeat) {
            repeating = !repeating;
            done = !repeating;
            if(repeating) {
                item.setIcon(getResources().getDrawable(R.drawable.ic_action_repeat_one));
                item.setTitle(R.string.onetime);
            }
            else {
                item.setIcon(getResources().getDrawable(R.drawable.ic_action_repeat));
                item.setTitle(R.string.repeat);
            }
            return true;
        }

        if (id == R.id.action_pause) {
            done = true;
            exit = true;
            mIsRecording = false;
            return true;
        }

        if (id == R.id.action_view_list) {
            showList();
            return true;
        }

        if (id == R.id.action_togglehandfoot) {
            LayoutInflater li = LayoutInflater.from(activity);
            View promptsView = li.inflate(R.layout.handfoot, null);
            final CheckBox  lf=(CheckBox) promptsView.findViewById(R.id.leftfoot),
                            rf=(CheckBox) promptsView.findViewById(R.id.rightfoot),
                            lh=(CheckBox) promptsView.findViewById(R.id.lefthand),
                            rh=(CheckBox) promptsView.findViewById(R.id.righthand);

            lf.setChecked(handfoot[LF]);
            rf.setChecked(handfoot[RF]);
            lh.setChecked(handfoot[LH]);
            rh.setChecked(handfoot[RH]);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);

            TextView tv = (TextView) promptsView.findViewById(R.id.userPrompt);
            tv.setText(tv.getText());
            alertDialogBuilder.setView(promptsView);

            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    handfoot[LF]=lf.isChecked();
                                    handfoot[RF]=rf.isChecked();
                                    handfoot[LH]=lh.isChecked();
                                    handfoot[RH]=rh.isChecked();
                                }
                            })
                    .setNegativeButton(getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    dialog.cancel();
                                }
                            });

            alertDialog = alertDialogBuilder.create();
            alertDialog.show();
            return true;
        }

        if (id == R.id.action_folder) {
            if(N==0) return false;

            LayoutInflater li = LayoutInflater.from(activity);
            View promptsView = li.inflate(R.layout.filename, null);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);

            alertDialogBuilder.setView(promptsView);

            final EditText userInput = (EditText) promptsView.findViewById(R.id.editTextDialogUserInput);
            userInput.setText(getFilename(REACTIONTIME_FILE_EXT_CSV), TextView.BufferType.EDITABLE);

            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    saveRecording(userInput.getText().toString());
                                }
                            })
                    .setNegativeButton(getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    dialog.cancel();
                                }
                            });

            alertDialog = alertDialogBuilder.create();
            alertDialog.show();
            return true;
        }

        if (id == R.id.action_sounddetect) {
            LayoutInflater li = LayoutInflater.from(activity);
            View promptsView = li.inflate(R.layout.sounddetect, null);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);

            alertDialogBuilder.setView(promptsView);

            final TextView soundLevelTextView = (TextView) promptsView.findViewById(R.id.soundLevel);

            final SeekBar soundLevelBar = (SeekBar) promptsView.findViewById(R.id.soundLevelSeekBar);
            soundLevelBar.setProgress((int)(((double)soundLevel/MAXLEVEL)*100));
            soundLevelTextView.setText(soundLevelBar.getProgress()+"%");

            soundLevelBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    soundLevelTextView.setText(String.valueOf(progress)+"%");
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    if(soundLevelBar.getProgress() > 0)
                                        soundLevel = (int)(MAXLEVEL*(soundLevelBar.getProgress()/100.0));
                                }
                            })
                    .setNegativeButton(getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    dialog.cancel();
                                }
                            });

            alertDialog = alertDialogBuilder.create();
            alertDialog.show();
            return true;
        }

        if (id == R.id.action_vibrate) {
            vibrate = !vibrate;
            item.setChecked(vibrate);
            return true;
        }

        if (id == R.id.action_time) {
            LayoutInflater li = LayoutInflater.from(activity);
            View promptsView = li.inflate(R.layout.delaytimer, null);

            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);

            alertDialogBuilder.setView(promptsView);

            final TextView delayTimerTextView = (TextView) promptsView.findViewById(R.id.delay);

            final SeekBar delayTimerLevelBar = (SeekBar) promptsView.findViewById(R.id.delaytimerSeekBar);
            delayTimerLevelBar.setProgress(delaytimer);
            delayTimerTextView.setText(delaytimer+"ms"); //delayTimerLevelBar.getProgress());

            delayTimerLevelBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    delayTimerTextView.setText(String.valueOf(progress)+"ms");
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            alertDialogBuilder
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.ok),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                        delaytimer = delayTimerLevelBar.getProgress();
                                }
                            })
                    .setNegativeButton(getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,int id) {
                                    dialog.cancel();
                                }
                            });

            alertDialog = alertDialogBuilder.create();
            alertDialog.show();
            return true;
        }

        if (id == R.id.action_about) {
            about = new AboutDialog(activity);
            about.setTitle(getString(R.string.about) + " ReactionTime");
            about.show();
            return true;
        }

        return super.onOptionsItemSelected(item);
	}

    private void initRecorder() {
        if(mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();

            mRecorder = null;
        }

        mRecorder = findAudioRecord();
        mBuffer = new short[bufferSize];
    }

    public AudioRecord findAudioRecord() {
        for (int rate : mSampleRates) {
            try {
                bufferSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

                if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
                    // check if we can instantiate and have a success
                    AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, rate, AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, bufferSize);

                    RECORDER_SAMPLERATE = rate;
                    FREQUENCY=RECORDER_SAMPLERATE/10;

                    if (recorder.getState() == AudioRecord.STATE_INITIALIZED)
                        return recorder;
                }
            } catch (Exception e) {}
        }
        return null;
    }

	private void showList() {
        showList = true;
        final List<String> resultList = new LinkedList<String>();
        String s = "";

        if((handfoot[LF] || handfoot[RF] || handfoot[LH] || handfoot[RH]) && N > 0) {
            double [] sum = { 0, 0, 0, 0};
            int [] count = {0,0,0,0};
            for (int i = 0; i < N; i++) {
                sum[handfootLR[i]] = sum[handfootLR[i]] + results[i];
                count[handfootLR[i]] = count[handfootLR[i]] + 1;
            }

            resultList.add(getString(R.string.mean) + "\n" +
                    (count[LF] != 0 ? (convertLFLHRFRH(LF) + " " + String.format("%.3f", sum[LF] / count[LF]) + "s\n") : "")  +
                    (count[RF] != 0 ? (convertLFLHRFRH(RF) + " " + String.format("%.3f", sum[RF] / count[RF]) + "s\n") : "")  +
                    (count[LH] != 0 ? (convertLFLHRFRH(LH) + " " + String.format("%.3f", sum[LH] / count[LH]) + "s\n") : "")  +
                    (count[RH] != 0 ? (convertLFLHRFRH(RH) + " " + String.format("%.3f", sum[RH] / count[RH]) + "s\n") : "") );

            for (int i = 1; i <= N; i++)
                resultList.add( i + ") " + convertLFLHRFRH(handfootLR[i-1]) + " " + String.format("%.3f", results[i-1]) + "s");
        }
        else if (N > 0) {
            double sum=0;

            for (int i = 0; i < N; i++)
                sum = sum + results[i];

                resultList.add(getString(R.string.mean) + ": " + String.format("%.3f", sum / N) + "s");

            for (int i = 1; i <= N; i++)
                resultList.add(i + ") " + String.format("%.3f", results[i - 1]) + "s");
        }

        final CustomAdapter adapter = new CustomAdapter(this, resultList);

        listView.setAdapter(adapter);
        // Next screen comes in from left.
        viewFlipper.setInAnimation(this, R.anim.slide_in_from_left);
        // Current screen goes out from right.
        viewFlipper.setOutAnimation(this, R.anim.slide_out_to_right);
        // Display next screen.
        viewFlipper.showNext();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, final int position, long id) {
                if(position == 0) return;
                AlertDialog.Builder adb=new AlertDialog.Builder(ReactionTimeActivityPlus.this);
                adb.setTitle(getString(R.string.delete) + " " + position + "?");
                adb.setNegativeButton(getString(R.string.cancel), null);
                adb.setPositiveButton(getString(R.string.ok), new AlertDialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                        for(int i=position;i<N;i++) {                           // Mean is position 0
                            results[i - 1] = results[i];
                            handfootLR[i-1] = handfootLR[i];
                        }
                        N--;
                        resultList.clear();

                        if((handfoot[LF] || handfoot[RF] || handfoot[LH] || handfoot[RH]) && N > 0) {
                            double [] sum = { 0, 0, 0, 0};
                            int [] count = {0,0,0,0};
                            for (int i = 0; i < N; i++) {
                                sum[handfootLR[i]] = sum[handfootLR[i]] + results[i];
                                count[handfootLR[i]] = count[handfootLR[i]] + 1;
                            }



                            resultList.add(getString(R.string.mean) + "\n" +
                                    (count[LF] != 0 ? (convertLFLHRFRH(LF) + " " + String.format("%.3f", sum[LF] / count[LF]) + "s\n") : "")  +
                                    (count[RF] != 0 ? (convertLFLHRFRH(RF) + " " + String.format("%.3f", sum[RF] / count[RF]) + "s\n") : "")  +
                                    (count[LH] != 0 ? (convertLFLHRFRH(LH) + " " + String.format("%.3f", sum[LH] / count[LH]) + "s\n") : "")  +
                                    (count[RH] != 0 ? (convertLFLHRFRH(RH) + " " + String.format("%.3f", sum[RH] / count[RH]) + "s\n") : "") );

                            for (int i = 1; i <= N; i++)
                                resultList.add( i + ") " + convertLFLHRFRH(handfootLR[i-1]) + " " + String.format("%.3f", results[i-1]) + "s");
                        }
                        else if (N > 0) {
                            double sum=0;

                            for (int i = 0; i < N; i++)
                                sum = sum + results[i];

                            resultList.add(getString(R.string.mean) + ": " + String.format("%.3f", sum / N) + "s");

                            for (int i = 1; i <= N; i++)
                                resultList.add(i + ") " + String.format("%.3f", results[i - 1]) + "s");
                        }
                        adapter.notifyDataSetChanged();
                    }});
                adb.show();
            }
        });
        setReactionTimeLabel(" ");
    }

    private String convertLFLHRFRH( byte b) {
        switch(b) {
            case LF : return getString(R.string.leftfoot);
            case RF : return getString(R.string.rightfoot);
            case LH : return getString(R.string.lefthand);
            case RH : return getString(R.string.righthand);
            default : return "";
        }
    }

    private byte setsignalImage(final boolean [] handfoot) {
        byte i = LF;                                                                            // Default to valid value if none selected by user

        boolean done=false;
        if(handfoot != null && (handfoot[LF] || handfoot[RF] || handfoot[LH] || handfoot[RH]))  // Execute only if user selected
            do {
                i = (byte) (new Random().nextInt(4));

                switch (i) {
                    case LH: {
                        done = handfoot[LH];
                        break;
                    }
                    case RH: {
                        done = handfoot[RH];
                        break;
                    }
                    case LF: {
                        done = handfoot[LF];
                        break;
                    }
                    case RF: {
                        done = handfoot[RF];
                        break;
                    }
                }
            } while( !done );

        final byte n = i;

        runOnUiThread( new Runnable() {
            public void run() {
                if(handfoot == null || (!handfoot[LF] && !handfoot[RF] && !handfoot[LH] && !handfoot[RH])) waitimage.setImageResource(R.drawable.outline);
                else {
                    switch (n) {
                        case LH: {
                            waitimage.setImageResource(R.drawable.lefthand);
                            break;
                        }
                        case RH: {
                            waitimage.setImageResource(R.drawable.righthand);
                            break;
                        }
                        case LF: {
                            waitimage.setImageResource(R.drawable.leftfoot);
                            break;
                        }
                        case RF: {
                            waitimage.setImageResource(R.drawable.rightfoot);
                            break;
                        }
                    }
                }
            }
        });
        return i;
    }

    private void toggleButtonVisibility(final int currentState) {
        runOnUiThread( new Runnable() {
            public void run() {
                switch(currentState) {
                    case STARTSTATE : {
                        reactbutton.setVisibility(View.GONE);
                        waitimage.setVisibility(View.GONE);
                        startbutton.setVisibility(View.VISIBLE);
                        break;
                    }
                    case WAITINGSTATE : {
                        reactbutton.setVisibility(View.GONE);
                        waitimage.setVisibility(View.VISIBLE);
                        startbutton.setVisibility(View.GONE);
                        break;
                    }
                    case REACTSTATE : {
                        reactbutton.setVisibility(View.VISIBLE);
                        waitimage.setVisibility(View.GONE);
                        startbutton.setVisibility(View.GONE);
                        break;
                    }

                }
            }
        });
    }

	public void setReactionTimeLabel(final String s) {
		runOnUiThread( new Runnable() {
			public void run() {
				mReactionTime.setText( s );
			}
		});
	}

    private String getFilename(String ext) {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath, REACTIONTIME_FOLDER);

        if (!file.exists()) {
            file.mkdirs();
        }
        return (file.getAbsolutePath() + "/" + System.currentTimeMillis() + ext);
    }

    private void startBufferedWrite() {

        mRecording = getFile("raw");

		new Thread(new Runnable() {
			@Override
			public void run() {

                mRecorder.startRecording();

				DataOutputStream output = null;
				try {
					output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(mRecording)));
					while (mIsRecording) {
						int readSize = mRecorder.read(mBuffer, 0, mBuffer.length);
						for (int i = 0; i < readSize; i++) {
							output.writeShort(mBuffer[i]);
						}
					 }
				} catch (IOException e) {
					Toast.makeText(ReactionTimeActivityPlus.this, e.getMessage(), Toast.LENGTH_SHORT).show();
				} finally {
					if (output != null) {
						try {
							output.flush();
						} catch (IOException e) {
							Toast.makeText(ReactionTimeActivityPlus.this, e.getMessage(), Toast.LENGTH_SHORT)
									.show();
						} finally {
							try {
								output.close();
							} catch (IOException e) {
								Toast.makeText(ReactionTimeActivityPlus.this, e.getMessage(), Toast.LENGTH_SHORT)
										.show();
							}
						}
					}
				}
			}
		}).start();
	}

    private void rawToWave(final File rawFile, final File waveFile) throws IOException {

        int read;
        byte[] rawData = new byte[(int) rawFile.length()];
        DataInputStream input = null;
        try {
            input = new DataInputStream(new FileInputStream(rawFile));
            read = input.read(rawData);
        } finally {
            if (input != null) {
                input.close();
            }
        }

		DataOutputStream output = null;
		try {
			output = new DataOutputStream(new FileOutputStream(waveFile));
			// WAVE header
			// see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
			writeString(output, "RIFF"); // chunk id
			writeInt(output, 36 + rawData.length); // chunk size
			writeString(output, "WAVE"); // format
			writeString(output, "fmt "); // subchunk 1 id
			writeInt(output, 16); // subchunk 1 size
			writeShort(output, (short) 1); // audio format (1 = PCM)
			writeShort(output, (short) 1); // number of channels
			writeInt(output, RECORDER_SAMPLERATE); // sample rate
			writeInt(output, RECORDER_SAMPLERATE * 2); // byte rate
			writeShort(output, (short) 2); // block align
			writeShort(output, (short) 16); // bits per sample
			writeString(output, "data"); // subchunk 2 id
			writeInt(output, rawData.length); // subchunk 2 size

            output.write(rawData, 0, read);

		} finally {
			if (output != null) {
				output.close();
			}
		}
	}

	private File getFile(final String suffix) {
		return new File(Environment.getExternalStorageDirectory(), "reactiontime" + "." + suffix);
	}

    private void saveRecording(String outFilename) {
        PrintStream out = null;

        try {
            out = new PrintStream(new FileOutputStream(outFilename));

            out.println("N,"+  (String) getString(R.string.reactiontime_label));

            for(int i = 0; i<N; i++)
                out.println((i+1) + "," + results[i]);

            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeInt(final DataOutputStream output, final int value) throws IOException {
		output.write(value >> 0);
		output.write(value >> 8);
		output.write(value >> 16);
		output.write(value >> 24);
	}

	private void writeShort(final DataOutputStream output, final short value) throws IOException {
		output.write(value >> 0);
		output.write(value >> 8);
	}

	private void writeString(final DataOutputStream output, final String value) throws IOException {
		for (int i = 0; i < value.length(); i++) {
			output.write(value.charAt(i));
		}
	}
}