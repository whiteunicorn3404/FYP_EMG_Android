
package com.example.proto_emg.Module.Entity;

import android.util.Log;

import java.lang.reflect.Array;
import java.util.Arrays;

public class runfft {
    public static final String TAG = runfft.class.getSimpleName();
    int N = 512;
    double[] re, im, amp, ampwoDC;
    int samplingFreq;

    FFT asd;

    public runfft(int N,int samplingFreq){
        this.N = N;
        this.samplingFreq = samplingFreq;
        re = new double[N];
        im = new double[N];
        amp = new double[N/2+1];
        ampwoDC= new double[N/2];

        Arrays.fill(re, 0);
        Arrays.fill(im, 0);

        asd = new FFT(N);
    }


    protected double medfreq(double[] ampwoDC,int sampling_freq,int no_of_sample){
        double sum=0.0;
        double avg=0.0;
        double medfreq=0.0;
        double nyquist =sampling_freq/2;
        int N=no_of_sample;

        for (int j = 0; j < (N / 2); j++) {
            ampwoDC[j] = (Math.pow(ampwoDC[j],2));
        }
        sum = Arrays.stream(ampwoDC).sum();
        avg = sum/2;
        for (int i = 0; i < N/2; i++) {
            if(avg-ampwoDC[i]>0){
                avg-=ampwoDC[i];
            }else{
                if((avg/ampwoDC[i])>0)
                    medfreq=(nyquist/(N/2))*(i-1+(avg/ampwoDC[i]));
                break;
            }
        }
        Log.v(TAG,"out:"+sum);
        Log.v(TAG,"avg:"+avg);
        Log.v(TAG,"medfreq:"+medfreq);
        return medfreq;
    }

    public double calcMed(short[] in){
        if(in.length>N){
            Log.v(TAG,"Array length and N do not match!");
            return -1;
        }
        Arrays.fill(re,0);
        Arrays.fill(im,0);
        for (int i = 0; i < in.length; i++) {
            // re[i] = Math.pow(-1, i);
            re[i] = in[i]; //fills in data here
            // im[i] = 0;
        }
        asd.fft(re,im);

        for (int i = 0; i < N/2+1; i++) {
            if(i==0){
                amp[i]=Math.hypot(re[i],im[i]); // calculate the real+imaginary for DC
            }else{
                amp[i]=Math.hypot(re[i],im[i])*2; // calculate the real+imaginary for the first half (0,nyquist]
                ampwoDC[i-1]=amp[i];
            }
        }
        double med = medfreq(ampwoDC, samplingFreq, N);
        Log.v(TAG,Double.toString(med));
        return med;
    }
}