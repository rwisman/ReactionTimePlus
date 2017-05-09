/*
 *     Copyright (C) 2017  Raymond Wisman
 * 			Indiana University SE
 * 			April 7, 2017
 *
 * 	AudioSineWave produces a sine wave on audio.

    Credits: http://stackoverflow.com/questions/20889627/playing-repeated-audiotrack-in-android-activity

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details <http://www.gnu.org/licenses/>.

 */

    /*
        Static class data is not reclaimed or altered when Activity onDestroy method is called.
	    Each static class variable is normally created only once and
        maintains its binding while the Activity object is destroyed and recreated (e.g. after screen rotation).
     */

package edu.ius.rwisman.reactiontime;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

class AudioSineWave {										// Some phones require more than a single wave in buffer (e.g. Nexus 5)
    static AudioTrack audioTrack=null;
    static AudioManager audioManager=null;
    static byte [] sineWave=null;
    static boolean first = true;
    static int wavesSAMPLES;

    public static void initialize(final Context context, final int RECORDER_SAMPLERATE,          // AudioSineWave.initialize(activity,44100,2000,4,1,10);
                             final int FREQUENCY, final int LOOPCOUNT,
                             final double VOLUME, final int waves) {
        final int SAMPLES = RECORDER_SAMPLERATE/FREQUENCY;
        wavesSAMPLES = waves*SAMPLES;

        if(sineWave == null)
            sineWave = sineWave(SAMPLES, waves);

        if(audioManager == null)
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                (int)(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)*VOLUME),							// Set to fractional max volume
                AudioManager.MODE_NORMAL);

        if(audioTrack == null) {
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, RECORDER_SAMPLERATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, waves * SAMPLES * 2,
                    AudioTrack.MODE_STATIC);
            audioTrack.write(sineWave, 0, sineWave.length);
        }
    }


    public static void start() {
        audioTrack.setLoopPoints(0, wavesSAMPLES , -1);
        audioTrack.reloadStaticData();
        audioTrack.play();
    }

    public static void stop() {
        audioTrack.stop();
    }


    private static byte[] sineWave(int samplesPerWave, int waves) {
        int samples = samplesPerWave*waves;
        final double sample[] = new double[samples];
        final byte sineWave[] = new byte[2 * samples];

        double x;
        double xStep = (waves * 2 * Math.PI)/samples;
        int i;

        for (x=0.0, i=0; waves * 2 * Math.PI - x > 0.0001; x=x+xStep, ++i)
            sample[i] = Math.sin(x);

        // Convert normalized samples to 16 bit PCM data
        int idx = 0;
        for (double dVal : sample) {
            short val = (short) (dVal * 32767);
            sineWave[idx++] = (byte) (val & 0x00ff);
            sineWave[idx++] = (byte) ((val & 0xff00) >>> 8);
        }
        return sineWave;
    }
}

