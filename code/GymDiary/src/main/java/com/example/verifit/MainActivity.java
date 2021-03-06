package com.example.verifit;

import static androidx.constraintlayout.motion.utils.Oscillator.TAG;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Instrumentation;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.DatePicker;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.github.mikephil.charting.charts.PieChart;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.thegrizzlylabs.sardineandroid.DavResource;
import com.thegrizzlylabs.sardineandroid.Sardine;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.*;

public class MainActivity extends AppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener , DatePickerDialog.OnDateSetListener{

    // "Data Structures"
    public static Set<String> Days = new TreeSet<String>();
    public static ArrayList<WorkoutSet> Sets = new ArrayList<WorkoutSet>();
    public static ArrayList<WorkoutDay> Workout_Days = new ArrayList<WorkoutDay>();
    public static ArrayList<Exercise> KnownExercises = new ArrayList<Exercise>(); // Initialized with hardcoded exercises
    public static String date_selected; // Used for other activities to get the selected date, by default it's set to today
    public static HashMap<String,Double> VolumePRs = new HashMap<String,Double>();
    public static HashMap<String,Double> ActualOneRepMaxPRs = new HashMap<String,Double>();
    public static HashMap<String,Double> EstimatedOneRMPRs = new HashMap<String,Double>();
    public static HashMap<String,Double> MaxRepsPRs = new HashMap<String,Double>();
    public static HashMap<String,Double> MaxWeightPRs = new HashMap<String,Double>();
    public static HashMap<String,Double> LastTimeVolume = new HashMap<String,Double>(); // Holds last workout's volume for each exercise
    public ViewPager2 viewPager2; // View Pager that is used in main activity
    public static ArrayList<WorkoutDay> Infinite_Workout_Days = new ArrayList<WorkoutDay>(); // Used to populate the viewPager object in MainActivity with "infinite" days

    // For File I/O permissions
    public static final int READ_REQUEST_CODE = 42;
    public static final int PERMISSION_REQUEST_STORAGE = 1000;
    public static String EXPORT_FILENAME = "verifit_backup";



    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Hacky way to have the same code run in onRestart() as well
        onCreateStuff();
    }


    // General Initialization Stuff
    public void onCreateStuff()
    {
        initActivity();

        // Bottom Navigation Bar Intents
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation_view);
        bottomNavigationView.setSelectedItemId(R.id.home);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);


        // Date selected is by default today
        Date date_clicked = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        date_selected = dateFormat.format(date_clicked);
    }


    // When choosing date from DatePicker
    @Override
    public void onDateSet(DatePicker datePicker, int i, int i1, int i2)
    {
        i1++;
        String year = String.valueOf(i);
        String month;
        String day;

        month = String.format("%02d", i1);
        day = String.format("%02d", i2);

        String date_clicked = year+"-"+month+"-"+day;
        MainActivity.date_selected = date_clicked;

        // Start Intent
        Intent in = new Intent(getApplicationContext(), DayActivity.class);
        Bundle mBundle = new Bundle();


        // Send Date and start activity
        mBundle.putString("date", date_clicked);
        in.putExtras(mBundle);
        startActivity(in);

    }

    // You guessed it!
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void initActivity()
    {

        // Write permissions required only for <= Android 10
        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q))
        {
            askWritePermission();
        }

        setExportBackupName();

        // Rename top bar to something sensible
        getSupportActionBar().setTitle("GymDiary");

        // From Settings Activity when importing CSV
        Intent in = getIntent();

        String WhatToDO = null;
        WhatToDO = in.getStringExtra("doit");

        // If Intent coming from settings activity
        if(WhatToDO != null)
        {
            if(WhatToDO.equals("importcsv"))
            {
                fileSearch();
            }
            else if(WhatToDO.equals("exportcsv"))
            {
                // Android 11 and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                {
                    //saveFile();
                    writeFileSAF();
                }
                // Android 10 and below
                else
                {
                    writeFile();
                }

                // Or else nothing comes up
                initViewPager();
            }
            else if(WhatToDO.equals("exportwebdav"))
            {
                // After Loading Data Initialize ViewPager
                initViewPager();
            }
            // Data already saved, just init view pager
            else if(WhatToDO.equals("importwebdav"))
            {
                // After Loading Data Initialize ViewPager
                initViewPager();
            }
        }
        // No intent
        else
        {
            // Get WorkoutDays from shared preferences
            loadWorkoutData();

            // Get Known Exercises from shared preferences
            loadKnownExercisesData();

            // After Loading Data Initialize ViewPager
            initViewPager();
        }
    }

    // Ask/Check Write Permission
    public boolean checkWritePermission(String permission)
    {
        int check = ContextCompat.checkSelfPermission(this,permission);
        return (check == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    protected void onRestart()
    {
        // This was already there so I am not deleting it
        super.onRestart();

//        System.out.println("MainActivity::OnRestart()");

        // Get WorkoutDays from shared preferences
        loadWorkoutData();

        // Get Known Exercises from shared preferences
        loadKnownExercisesData();

        // After Loading Data Initialize ViewPager
        initViewPager();

        // Bottom Navigation Bar Intents
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation_view);
        bottomNavigationView.setSelectedItemId(R.id.home);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);
    }


    // Initialize View pager object
    public void initViewPager()
    {
        // Skip creation of empty workouts if you don't have to
        if(Infinite_Workout_Days.isEmpty())
        {
            // "Infinite" Data Structure
            Infinite_Workout_Days.clear();

            // Find start and End Dates
            Calendar c = Calendar.getInstance();
            c.setTime(new Date());
            c.add(Calendar.YEAR, -5);
            Date startDate = c.getTime();
            c.add(Calendar.YEAR, +10);
            Date endDate = c.getTime();

            // Create Calendar Objects that represent start and end date
            Calendar start = Calendar.getInstance();
            start.setTime(startDate);
            Calendar end = Calendar.getInstance();
            end.setTime(endDate);

            // Construct 20 years worth of empty workout days
            for (Date date = start.getTime(); start.before(end); start.add(Calendar.DATE, 1), date = start.getTime())
            {
                // Get Date in String format
                String date_str = new SimpleDateFormat("yyyy-MM-dd").format(date);

                // Create new mostly empty object
                WorkoutDay today = new WorkoutDay();
                today.setDate(date_str);
                Infinite_Workout_Days.add(today);
            }
        }

        // Use View Pager with Infinite Days
        viewPager2 = findViewById(R.id.viewPager2);
        viewPager2.setAdapter(new ViewPagerWorkoutDayAdapter(this,Infinite_Workout_Days));
        viewPager2.setCurrentItem((Infinite_Workout_Days.size()+1)/2); // Navigate to today
    }

    // Formats backup name in case of export
    public static void setExportBackupName()
    {
        EXPORT_FILENAME = "verifit";
        Format formatter = new SimpleDateFormat("_yyyy-MM-dd_HH:mm:ss");
        String str_date = formatter.format(new Date());
        EXPORT_FILENAME = EXPORT_FILENAME + str_date;
    }



    // Get the results after user gives/denies permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode)
        {
            case 30:
                if(grantResults.length > 0)
                {
                    boolean readper = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean writeper = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                    if(readper && writeper)
                    {
                        Toast.makeText(getApplicationContext(), "???? ???????c cho ph??p", Toast.LENGTH_SHORT).show();
                    }
                    else
                    {
                        Toast.makeText(getApplicationContext(), "Quy???n b??? T??? ch???i", Toast.LENGTH_SHORT).show();
                    }
                }
                else
                {
                    Toast.makeText(getApplicationContext(), "B???n ???? t??? ch???i quy???n", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }


    // Returns index of day
    public static int getDayPosition(String Date)
    {
        for(int i = 0; i < MainActivity.Workout_Days.size(); i++)
        {
            if(MainActivity.Workout_Days.get(i).getDate().equals(Date))
            {
                return i;
            }
        }
        return -1;
    }


    // Returns index of exercise
    public static int getExercisePosition(String Date, String exerciseName)
    {

        int day_position = getDayPosition(Date);

        // The day doesn't even have an exercise
        if (day_position == -1)
        {
            return -1;
        }



        ArrayList<WorkoutExercise> Exercises = MainActivity.Workout_Days.get(day_position).getExercises();

        for(int i = 0; i < Exercises.size(); i++)
        {
            if(Exercises.get(i).getExercise().equals(exerciseName))
            {
                return i;
            }
        }

        return -1;
    }


    // Select a file using the build in file manager
    public void fileSearch()
    {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent,READ_REQUEST_CODE);
    }


    // When File explorer stops this function runs
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                System.out.println("Makis uri: " + uri.toString());
                if (requestCode == READ_REQUEST_CODE) {

                    readFileSAF(uri);
                    System.out.println("Makis READ_REQUEST_CODE");

                }
            }
        }

// Old stuff
//        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
//            if (data != null) {
//                Uri uri = data.getData();
//                String filename = uri.getPath();
//                filename = filename.substring(filename.indexOf(":") + 1);
//                readFile(filename);
//                saveWorkoutData(this);
//            }
//        }
    }


    // Converts CSV file to Internally used Dat Structure
    public static void CSVtoSets(List csvList)
    {
        // Remove potential Duplicates
        MainActivity.Sets.clear();

        // i = 1 since first row is only Strings
        for(int i = 1; i < csvList.size(); i++)
        {



            String[] row = (String[]) csvList.get(i);

//            System.out.println(row.length);

            String Date = row[0];
            String Exercise = row[1];
            String Category = row[2];
            String Reps = row[3];
            String Weight = row[4];

//            System.out.println(row[5]);

            String Comment = "";

            if(row.length == 6)
            {
                Comment = row[5];
            }

            // String Comment = row[]

            WorkoutSet workoutSet = new WorkoutSet(Date,Exercise,Category,Double.parseDouble(Weight),Double.parseDouble(Reps),Comment);
            Sets.add(workoutSet);
        }
    }


    // Updates All other Data Structures
    public static void SetsToEverything()
    {
        // Clear Data Structures
        MainActivity.Days.clear();
        Workout_Days.clear();

        // i = 1 since first row is only Strings
        for(int i = 0; i < Sets.size(); i++)
        {
            MainActivity.Days.add(Sets.get(i).getDate());
        }


        Iterator<String> it = MainActivity.Days.iterator();

        // Construct Workout_Days Array List
        while (it.hasNext())
        {
            String Date = it.next();

            WorkoutDay temp_day = new WorkoutDay();
            ArrayList<WorkoutSet> temp_day_sets = new ArrayList<WorkoutSet>();

            // For all Sets
            for(int i = 0; i < Sets.size(); i++)
            {
                // If Date matches add Set Object to Workout_Day Object
                if(Date.equals(Sets.get(i).getDate()))
                {
                    temp_day.addSet(Sets.get(i));
                }
            }
            MainActivity.Workout_Days.add(temp_day);
        }
    }


    // Initialized KnownExercises ArrayList with some hardcoded exercises
    public void initKnownExercises()
    {
        KnownExercises.clear();
        // Some hardcoded Exercises
        KnownExercises.add(new Exercise("?????y ng???c tr??n gh??? b???ng v???i t??? ????n","Ng????c"));
        KnownExercises.add(new Exercise("Ch???ng ?????y","Ng???c"));
        KnownExercises.add(new Exercise("Banh ng???c v???i t??? ????n","Ng???c"));
        KnownExercises.add(new Exercise("?????y ng???c d???c l??n v???i t??? ????n","Ng???c"));
        KnownExercises.add(new Exercise("??????y ng????c v????i ta?? tay","Ng???c"));
        KnownExercises.add(new Exercise("Ma??y e??p ng????c","Ng???c"));
        KnownExercises.add(new Exercise("??????y vai ng????i","Vai"));
        KnownExercises.add(new Exercise("Vai sau v????i ta?? tay","Vai"));
        KnownExercises.add(new Exercise("Ke??o ca??p","Vai"));
        KnownExercises.add(new Exercise("N??ng ta?? vai tr??????c","Vai"));
        KnownExercises.add(new Exercise("??????ng ta??c v???? ca??nh tay","Vai"));
        KnownExercises.add(new Exercise("Cha??y b????","B???p tay"));
        KnownExercises.add(new Exercise("Ma??y t????p ch??n","Ch??n"));
        KnownExercises.add(new Exercise("Squat","Ch??n"));
        KnownExercises.add(new Exercise("B??????c c????t ke??o","Ch??n"));
        KnownExercises.add(new Exercise("G????p bu??ng","bu??ng"));
        KnownExercises.add(new Exercise("Deadlift","Body"));

    }


    // Calculate all Volume Personal Records from scratch
    public static void calculatePersonalRecords()
    {
        MainActivity.VolumePRs.clear();
        MainActivity.ActualOneRepMaxPRs.clear();
        MainActivity.EstimatedOneRMPRs.clear();
        MainActivity.MaxRepsPRs.clear();
        MainActivity.MaxWeightPRs.clear();
        MainActivity.LastTimeVolume.clear();

        // Initialize Volume Record Hashmap
        for(int i = 0; i < MainActivity.KnownExercises.size(); i++)
        {
            MainActivity.VolumePRs.put((MainActivity.KnownExercises.get(i).getName()),0.0);
            MainActivity.ActualOneRepMaxPRs.put((MainActivity.KnownExercises.get(i).getName()),0.0);
            MainActivity.EstimatedOneRMPRs.put((MainActivity.KnownExercises.get(i).getName()),0.0);
            MainActivity.MaxRepsPRs.put((MainActivity.KnownExercises.get(i).getName()),0.0);
            MainActivity.MaxWeightPRs.put((MainActivity.KnownExercises.get(i).getName()),0.0);
            MainActivity.LastTimeVolume.put((MainActivity.KnownExercises.get(i).getName()),0.0);
        }

        // Calculate Volume PRs
        for(int i = 0; i < MainActivity.KnownExercises.size(); i++)
        {
            for(int j = 0; j < MainActivity.Workout_Days.size(); j++)
            {
                for(int k = 0; k < MainActivity.Workout_Days.get(j).getExercises().size(); k++)
                {
                    if(MainActivity.Workout_Days.get(j).getExercises().get(k).getExercise().equals(MainActivity.KnownExercises.get(i).getName()))
                    {
                        // Volume Personal Records
                        if(VolumePRs.get(MainActivity.KnownExercises.get(i).getName()) < (MainActivity.Workout_Days.get(j).getExercises().get(k).getVolume()))
                        {
                            MainActivity.Workout_Days.get(j).getExercises().get(k).setVolumePR(true);
                            VolumePRs.put(MainActivity.KnownExercises.get(i).getName(),MainActivity.Workout_Days.get(j).getExercises().get(k).getVolume());
                        }

                        // Actual One Repetition Maximum
                        if(ActualOneRepMaxPRs.get(MainActivity.KnownExercises.get(i).getName()) < (MainActivity.Workout_Days.get(j).getExercises().get(k).getActualOneRepMax()))
                        {
                            MainActivity.Workout_Days.get(j).getExercises().get(k).setActualOneRepMaxPR(true);
                            ActualOneRepMaxPRs.put(MainActivity.KnownExercises.get(i).getName(),MainActivity.Workout_Days.get(j).getExercises().get(k).getActualOneRepMax());
                        }

                        // Estimated One Repetition Maximum
                        if(EstimatedOneRMPRs.get(MainActivity.KnownExercises.get(i).getName()) < (MainActivity.Workout_Days.get(j).getExercises().get(k).getEstimatedOneRepMax()))
                        {
                            MainActivity.Workout_Days.get(j).getExercises().get(k).setEstimatedOneRepMaxPR(true);
                            EstimatedOneRMPRs.put(MainActivity.KnownExercises.get(i).getName(),MainActivity.Workout_Days.get(j).getExercises().get(k).getEstimatedOneRepMax());
                        }

                        // Max Repetitions Personal Records
                        if(MaxRepsPRs.get(MainActivity.KnownExercises.get(i).getName()) < (MainActivity.Workout_Days.get(j).getExercises().get(k).getMaxReps()))
                        {
                            MainActivity.Workout_Days.get(j).getExercises().get(k).setMaxRepsPR(true);
                            MaxRepsPRs.put(MainActivity.KnownExercises.get(i).getName(),MainActivity.Workout_Days.get(j).getExercises().get(k).getMaxReps());
                        }

                        // Max Weight Personal Records
                        if(MaxWeightPRs.get(MainActivity.KnownExercises.get(i).getName()) < (MainActivity.Workout_Days.get(j).getExercises().get(k).getMaxWeight()))
                        {
                            MainActivity.Workout_Days.get(j).getExercises().get(k).setMaxWeightPR(true);
                            MaxWeightPRs.put(MainActivity.KnownExercises.get(i).getName(),MainActivity.Workout_Days.get(j).getExercises().get(k).getMaxWeight());
                        }

                        // Harder Than Last Time!
                        if(LastTimeVolume.get(MainActivity.KnownExercises.get(i).getName()) < (MainActivity.Workout_Days.get(j).getExercises().get(k).getVolume()))
                        {
                            MainActivity.Workout_Days.get(j).getExercises().get(k).setHTLT(true);
                            LastTimeVolume.put(MainActivity.KnownExercises.get(i).getName(),MainActivity.Workout_Days.get(j).getExercises().get(k).getVolume());
                        }
                        // This needs to be updates since we are dealing with last time and not overall maximums
                        else
                        {
                            LastTimeVolume.put(MainActivity.KnownExercises.get(i).getName(),MainActivity.Workout_Days.get(j).getExercises().get(k).getVolume());
                        }



                    }
                }
            }
        }
    }

    // Saves Workout_Days Array List in shared preferences
    // For some reason when I pass the context it works so let's roll with it :D
    public static void saveWorkoutData(Context ct)
    {
        SharedPreferences sharedPreferences = ct.getSharedPreferences("shared preferences",MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(Workout_Days);
        editor.putString("workouts",json);
        editor.apply();
    }

    // Loads Workout_Days Array List from shared preferences
    public void loadWorkoutData()
    {
        if(Workout_Days.isEmpty())
        {
            SharedPreferences sharedPreferences = getSharedPreferences("shared preferences",MODE_PRIVATE);
            Gson gson = new Gson();
            String json = sharedPreferences.getString("workouts",null);
            Type type = new TypeToken<ArrayList<WorkoutDay>>(){}.getType();
            Workout_Days = gson.fromJson(json,type);

            // If there are no previously saved entries make a new object
            if(Workout_Days == null)
            {
                Workout_Days = new ArrayList<WorkoutDay>();
            }
        }


    }

    // Saves Workout_Days Array List in shared preferences
    // For some reason when I pass the context it works so let's roll with it :D
    public static void saveKnownExerciseData(Context ct)
    {
        SharedPreferences sharedPreferences = ct.getSharedPreferences("shared preferences",MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(KnownExercises);
        editor.putString("known_exercises",json);
        editor.apply();
    }

    // Loads Workout_Days Array List from shared preferences
    public void loadKnownExercisesData()
    {
        if(KnownExercises.isEmpty())
        {
            SharedPreferences sharedPreferences = getSharedPreferences("shared preferences",MODE_PRIVATE);
            Gson gson = new Gson();
            String json = sharedPreferences.getString("known_exercises",null);
            Type type = new TypeToken<ArrayList<Exercise>>(){}.getType();
            KnownExercises = gson.fromJson(json,type);

            // If there are no previously saved entries make a new object
            if(KnownExercises == null || KnownExercises.isEmpty())
            {
                KnownExercises = new ArrayList<Exercise>();
                initKnownExercises();
            }
        }
    }

    // Read CSV from internal storage
    public void readFile(String filename)
    {
        List csvList = new ArrayList();

        try {
            System.out.println(Environment.getExternalStorageDirectory());
            File textFile = new File(Environment.getExternalStorageDirectory(), filename);
            FileInputStream inputStream = new FileInputStream(textFile);
            CSVFile csvFile = new CSVFile(inputStream);
            csvList = csvFile.read();

            // Here is where the magic happens
            CSVtoSets(csvList); // Read File and Construct Local Objects
            SetsToEverything(); // Convert Set Objects to Day Objects
            System.out.println("csv to known...");
            csvToKnownExercises(); // Find all Exercises in CSV and add them to known exercises
            saveKnownExerciseData(this); // Save KnownExercises in CSV
            saveWorkoutData(this); // Save WorkoutDays in Shared Preferences
            initViewPager(); // Initialize View Pager
        }
        catch (IOException e)
        {
            System.out.println(e.getMessage());
            Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
            Toast.makeText(getApplicationContext(), "Could not locate file " + filename,Toast.LENGTH_SHORT).show();
            // Avoid Errors
            clearDataStructures();
        }

    }

    // Read CSV from internal storage
    public void readFileSAF(Uri uri)
    {
        List csvList = new ArrayList();

        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            CSVFile csvFile = new CSVFile(inputStream);
            csvList = csvFile.read();

            // Here is where the magic happens
            CSVtoSets(csvList); // Read File and Construct Local Objects
            SetsToEverything(); // Convert Set Objects to Day Objects
            System.out.println("csv to known...");
            csvToKnownExercises(); // Find all Exercises in CSV and add them to known exercises
            saveKnownExerciseData(this); // Save KnownExercises in CSV
            saveWorkoutData(this); // Save WorkoutDays in Shared Preferences
            initViewPager(); // Initialize View Pager
        }
        catch (IOException e)
        {
            System.out.println(e.getMessage());
            Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
            Toast.makeText(getApplicationContext(), "Could not locate file " + uri.getPath(),Toast.LENGTH_SHORT).show();
            // Avoid Errors
            clearDataStructures();
        }

    }

    // Stevdza-San Tutorial
    private boolean isExternalStorageWritable()
    {
        if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
        {
            return true;
        }
        else
        {
            return false;
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    // Android 11 and above
    public void saveFile()
    {

        String FolderName = "Verifit";

        // Test
        InputStream is = new ByteArrayInputStream("mContent".getBytes());
        OutputStream outputStream = null;
        String name =  EXPORT_FILENAME;
        String path = Environment.DIRECTORY_DOCUMENTS+File.separator+"Verifit/";
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME ,name);
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH ,path);
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
        if(uri!=null){
            try {
                outputStream = getContentResolver().openOutputStream(uri);

                outputStream.write("Date,Exercise,Category,Weight (kg),Reps,Comment\n".getBytes());

                for(int i = 0; i < MainActivity.Workout_Days.size(); i++)
                {
                    for(int j = 0; j < MainActivity.Workout_Days.get(i).getExercises().size(); j++)
                    {
                        String exerciseComment = MainActivity.Workout_Days.get(i).getExercises().get(j).getComment();
                        for(int k=0; k < MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().size(); k++)
                        {
                            String Date = MainActivity.Workout_Days.get(i).getExercises().get(j).getDate();
                            String exerciseName = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getExercise();
                            String exerciseCategory = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getCategory();
                            Double Weight = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getWeight();
                            Double Reps = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getReps();
                            outputStream.write((Date + "," + exerciseName+ "," + exerciseCategory + "," + Weight + "," + Reps + "," + exerciseComment + "\n").getBytes());
                        }
                    }
                }

                Toast.makeText(getApplicationContext(), "Backup saved in " + path, Toast.LENGTH_LONG).show();
                System.out.println("Backup saved in " + path);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
            }finally {
                try {
                    is.close();
                    outputStream.close();

                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
                }

            }
        }
    }

    // Ask For File I/O Permissions
    public void askWritePermission()
    {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_STORAGE);
        }
    }

    // Stevdza-San Tutorial
    @RequiresApi(api = Build.VERSION_CODES.O)
    // Android 10 and below
    public void writeFile()
    {
        if(isExternalStorageWritable() && checkWritePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        {
            // The folder where everything is stored
            String verifit_folder = "verifit";

            // Print Root Directory (sanity check)
            System.out.println(Environment.getExternalStorageDirectory());
            System.out.println(Environment.getExternalStoragePublicDirectory("verifit"));

            // Create verifit path
            Path path = Environment.getExternalStorageDirectory().toPath();
            path = Paths.get(path + verifit_folder);

            // If verifit path doesn't exist create it
            if (Files.exists(path))
            {
                System.out.println("This path exists");
            }
            else
            {
                System.out.println("This path does not exist");

                File folder = new File(Environment.getExternalStorageDirectory() + File.separator + verifit_folder);
                boolean success = true;
                if (!folder.exists()) {
                    success = folder.mkdirs();
                }
                if (success) {
                    System.out.println("Verifit folder has been created");
                } else {
                    System.out.println("Verifit folder has not been created");
                }

            }

            // Write file in the verifit path
            File textfile = new File(Environment.getExternalStoragePublicDirectory("verifit"), EXPORT_FILENAME);
            try
            {
                FileOutputStream fos = new FileOutputStream(textfile);
                fos.write("Date,Exercise,Category,Weight (kg),Reps,Comment\n".getBytes());

                for(int i = 0; i < MainActivity.Workout_Days.size(); i++)
                {
                    for(int j = 0; j < MainActivity.Workout_Days.get(i).getExercises().size(); j++)
                    {
                        String exerciseComment = MainActivity.Workout_Days.get(i).getExercises().get(j).getComment();
                        for(int k=0; k < MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().size(); k++)
                        {
                            String Date = MainActivity.Workout_Days.get(i).getExercises().get(j).getDate();
                            String exerciseName = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getExercise();
                            String exerciseCategory = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getCategory();
                            Double Weight = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getWeight();
                            Double Reps = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getReps();
                            fos.write((Date + "," + exerciseName+ "," + exerciseCategory + "," + Weight + "," + Reps + "," + exerciseComment + "\n").getBytes());
                        }
                    }
                }
                fos.close();
                Toast.makeText(getApplicationContext(), "File Written in " + Environment.getExternalStorageDirectory(), Toast.LENGTH_SHORT).show();

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                    int importance = NotificationManager.IMPORTANCE_DEFAULT;
                    NotificationChannel channel = new NotificationChannel("My notification","Gym App", importance);
                    NotificationManager notificationManager = getSystemService(NotificationManager.class);
                    notificationManager.createNotificationChannel(channel);
                }
                NotificationCompat.Builder notiBuilder = new NotificationCompat.Builder(MainActivity.this, "My notification")
                        .setSmallIcon(R.drawable.ic_fitness_center_24px)
                        .setContentTitle("???? l??u th??nh c??ng")
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText("???? export file th??nh c??ng. B???n c?? th??? v??o th?? m???c File -> Document ????? ki???m tra"))
                        .setContentText("???? export file th??nh c??ng. B???n c?? th??? v??o th?? m???c File -> Document ????? ki???m tra");
                NotificationManagerCompat managerCompat = NotificationManagerCompat.from(MainActivity.this);
                managerCompat.notify(1, notiBuilder.build());
            }
            catch (IOException e)
            {
                System.out.println(e.getMessage());
                Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
            }
        }
        else
        {
            Toast.makeText(getApplicationContext(), "External Storage Not Writable", Toast.LENGTH_SHORT).show();
        }
    }

    // Export backup function using Storage Access Framework
    public void writeFileSAF()
    {
        String fileName = EXPORT_FILENAME;

        try
        {
            OutputStream outputStream;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName); // file name required to contain extestion file mime
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS+"/Verifit"); //DIRECTORY
                Uri extVolumeUri = MediaStore.Files.getContentUri("external");
                Uri fileUri = getApplicationContext().getContentResolver().insert(extVolumeUri, values);
                outputStream = getApplicationContext().getContentResolver().openOutputStream(fileUri);
            }
            else {
                File root = new File(Environment.getExternalStorageDirectory()+File.separator+"DIRECTORY_NAME", "images");
                File file = new File(root, fileName );
                Log.d(TAG, "saveFile: file path - " + file.getAbsolutePath());
                outputStream = new FileOutputStream(file);
            }
            outputStream.write("Date,Exercise,Category,Weight (kg),Reps,Comment\n".getBytes());

            for(int i = 0; i < MainActivity.Workout_Days.size(); i++)
            {
                for(int j = 0; j < MainActivity.Workout_Days.get(i).getExercises().size(); j++)
                {
                    String exerciseComment = MainActivity.Workout_Days.get(i).getExercises().get(j).getComment();
                    for(int k=0; k < MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().size(); k++)
                    {
                        String Date = MainActivity.Workout_Days.get(i).getExercises().get(j).getDate();
                        String exerciseName = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getExercise();
                        String exerciseCategory = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getCategory();
                        Double Weight = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getWeight();
                        Double Reps = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getReps();
                        outputStream.write((Date + "," + exerciseName+ "," + exerciseCategory + "," + Weight + "," + Reps + "," + exerciseComment + "\n").getBytes());
                    }
                }
            }
            outputStream.close();
            //Ph?? s???a
            if (Build.VERSION.SDK_INT >= 19){
                Toast.makeText(getApplicationContext(), "Backup saved in " + Environment.DIRECTORY_DOCUMENTS+"/Verifit" , Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(getApplicationContext(), "Backup saved in " + "/Documents"+"/Verifit" , Toast.LENGTH_LONG).show();
            }

        }
        catch (Exception e)
        {
            System.out.println(e.toString());
            Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    // Clears all locally used data structures
    public void clearDataStructures()
    {
        // Clear everything just in case
        this.Workout_Days.clear();
        this.KnownExercises.clear(); // This removes all known exercises
        this.Sets.clear();
        this.Days.clear();
        saveWorkoutData(this);
        saveKnownExerciseData(this);
    }

    // Inefficient bubble sort but does the job
    public static void sortWorkoutDaysDate()
    {
        Collections.sort(MainActivity.Workout_Days, new Comparator<WorkoutDay>() {
            @Override
            public int compare(WorkoutDay workoutDay, WorkoutDay t1)
            {
                String date1 = workoutDay.getDate();
                String date2 = t1.getDate();
                Date date_object1 = new Date();
                Date date_object2 = new Date();

                try {
                    date_object1 = new SimpleDateFormat("yyyy-MM-dd").parse(date1);
                    date_object2 = new SimpleDateFormat("yyyy-MM-dd").parse(date2);
                }
                catch (Exception e)
                {
                    System.out.println(e.getMessage());
                }
                return date_object1.compareTo(date_object2);
            }
        });
    }

    // Just Check if an exercise is known
    public static boolean doesExerciseExist(String exercise_name)
    {
        for(int i = 0; i < KnownExercises.size(); i++)
        {
            if(KnownExercises.get(i).getName().equals(exercise_name))
            {
                return true;
            }
        }
        return false;
    }

    // Returns the exercise category if exists, else it returns an empty string
    public static String getExerciseCategory(String Exercise)
    {
        for(int i = 0; i < KnownExercises.size(); i++)
        {
            if(KnownExercises.get(i).getName().equals(Exercise))
            {
                return KnownExercises.get(i).getBodyPart();
            }
        }
        return "";
    }

    // Deletes exercise from KnownExercises and from WorkoutDays
    public static void deleteExercise(String exercise_name)
    {
        // Iterate Workout Days
        for (Iterator<WorkoutDay> dayIterator = MainActivity.Workout_Days.iterator(); dayIterator.hasNext(); )
        {

            WorkoutDay currentDay = dayIterator.next();

            // Iterate Workout Exercises
            for(Iterator<WorkoutExercise> exerciseIterator = currentDay.getExercises().iterator(); exerciseIterator.hasNext();)
            {
                WorkoutExercise current_exercise = exerciseIterator.next();
                if(current_exercise.getExercise().equals(exercise_name))
                {
                    exerciseIterator.remove();
                }
            }

            // Iterate Workout Sets
            for(Iterator<WorkoutSet> setIterator = currentDay.getSets().iterator(); setIterator.hasNext();)
            {
                WorkoutSet current_set = setIterator.next();
                if(current_set.getExercise().equals(exercise_name))
                {
                    setIterator.remove();
                }
            }


            if(currentDay.getSets().isEmpty())
            {
                dayIterator.remove();
            }
        }

        // Iterate Known Exercises data structure
        for(Iterator<Exercise> exerciseIterator = MainActivity.KnownExercises.iterator(); exerciseIterator.hasNext();)
        {
            Exercise current_exercise = exerciseIterator.next();

            if(current_exercise.getName().equals(exercise_name))
            {
                exerciseIterator.remove();
            }
        }
    }

    // Changes exercise name and body part
    public static void editExercise(String exercise_name, String new_exercise_name, String new_exercise_bodypart)
    {
        for(int i = 0; i < MainActivity.KnownExercises.size(); i++)
        {
            if(KnownExercises.get(i).getName().equals(exercise_name))
            {
                KnownExercises.get(i).setName(new_exercise_name);
                KnownExercises.get(i).setBodyPart(new_exercise_bodypart);
            }
        }

        for(int i = 0; i < MainActivity.Workout_Days.size(); i++)
        {
            for(int j = 0; j < MainActivity.Workout_Days.get(i).getSets().size(); j++)
            {
                if(MainActivity.Workout_Days.get(i).getSets().get(j).getExercise().equals(exercise_name))
                {
                    MainActivity.Workout_Days.get(i).getSets().get(j).setExercise(new_exercise_name);
                    MainActivity.Workout_Days.get(i).getSets().get(j).setCategory(new_exercise_bodypart);
                }
            }

            for(int j = 0; j < MainActivity.Workout_Days.get(i).getExercises().size(); j++)
            {
                if(MainActivity.Workout_Days.get(i).getExercises().get(j).getExercise().equals(exercise_name))
                {
                    MainActivity.Workout_Days.get(i).getExercises().get(j).setExercise(new_exercise_name);

                    for(int k = 0; k < MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().size(); k++)
                    {
                        if(MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getExercise().equals(exercise_name))
                        {
                            MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).setExercise(new_exercise_name);
                            MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).setCategory(new_exercise_bodypart);
                        }
                    }


                }
            }
        }

    }

    // Add all exercises found in the csv to the Known Exercises local data structure
    public static void csvToKnownExercises()
    {
        // Make new ArrayList which will hold duplicates
        ArrayList<Exercise> DuplicateKnownExercises = new ArrayList<>();

        for(int i = 0; i < MainActivity.Workout_Days.size(); i++)
        {
            for(int j = 0; j < MainActivity.Workout_Days.get(i).getSets().size(); j++)
            {
                String Name = MainActivity.Workout_Days.get(i).getSets().get(j).getExercise();
                String Bodypart = MainActivity.Workout_Days.get(i).getSets().get(j).getCategory();
                DuplicateKnownExercises.add(new Exercise(Name,Bodypart));
            }
        }


        // Known Exercises is empty at this point but doesn't hurt to clear anyway
        // MainActivity.KnownExercises.clear();

        // Manual Implementation "borrowed" from stack overflow
        for (Exercise event : DuplicateKnownExercises) {
            boolean isFound = false;
            // check if the event name exists in noRepeat
            for (Exercise e : MainActivity.KnownExercises) {
                if (e.getName().equals(event.getName()) || (e.equals(event))) {
                    isFound = true;
                    break;
                }
            }
            if (!isFound) KnownExercises.add(event);
        }
    }


    public static void exportWebDav(Context context, String webdavurl, String webdavusername, String webdavpassword)
    {
        // Enable networking on main thread
        StrictMode.ThreadPolicy gfgPolicy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(gfgPolicy);

        // Sardine Stuff
        Sardine sardine = new OkHttpSardine();
        sardine.setCredentials(webdavusername, webdavpassword);

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            output.write("Date,Exercise,Category,Weight (kg),Reps,Comment\n".getBytes());
            for(int i = 0; i < MainActivity.Workout_Days.size(); i++)
            {
                for(int j = 0; j < MainActivity.Workout_Days.get(i).getExercises().size(); j++)
                {
                    String exerciseComment = MainActivity.Workout_Days.get(i).getExercises().get(j).getComment();
                    for(int k=0; k < MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().size(); k++)
                    {
                        String Date = MainActivity.Workout_Days.get(i).getExercises().get(j).getDate();
                        String exerciseName = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getExercise();
                        String exerciseCategory = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getCategory();
                        Double Weight = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getWeight();
                        Double Reps = MainActivity.Workout_Days.get(i).getExercises().get(j).getSets().get(k).getReps();
                        output.write((Date + "," + exerciseName+ "," + exerciseCategory + "," + Weight + "," + Reps + "," + exerciseComment + "\n").getBytes());
                    }
                }
            }
            output.close();

            byte[] data = output.toByteArray();
            setExportBackupName();
            sardine.put(webdavurl+ EXPORT_FILENAME+".txt", data);
            Toast.makeText(context, "Backup saved in " + webdavurl + EXPORT_FILENAME+".txt" , Toast.LENGTH_SHORT).show();
            System.out.println("Test Final");


            // This is done to somehow run initViewPager()
            Intent in = new Intent(context, MainActivity.class);
            in.putExtra("doit", "exportwebdav");
            context.startActivity(in);



        }
        catch (Exception e)
        {
            System.out.println(e.toString());
            Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void importWebDav(Context context, String webdavurl, String webdavusername, String webdavpassword, String webdavresourcename)
    {
        // Enable networking on main thread
        StrictMode.ThreadPolicy gfgPolicy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(gfgPolicy);

        // Sardine Stuff
        Sardine sardine = new OkHttpSardine();
        sardine.setCredentials(webdavusername, webdavpassword);

        // Initalize for file I/O
        InputStream inputStream;
        List csvList = new ArrayList();

        try {

            List<DavResource> resources = sardine.list(webdavurl);


            for (DavResource res : resources)
            {
                //System.out.println("Resources: " + res.getName());
                if(res.getName().equals(webdavresourcename))
                {
                    System.out.println("Found it!");

                    inputStream = sardine.get(webdavurl+res.getName());

                    CSVFile csvFile = new CSVFile(inputStream);
                    csvList = csvFile.read();

                    // Here is where the magic happens
                    CSVtoSets(csvList); // Read File and Construct Local Objects
                    SetsToEverything(); // Convert Set Objects to Day Objects
                    System.out.println("csv to known...");
                    csvToKnownExercises(); // Find all Exercises in CSV and add them to known exercises
                    saveKnownExerciseData(context); // Save KnownExercises in CSV
                    saveWorkoutData(context); // Save WorkoutDays in Shared Preferences
                    //initViewPager(); // Commenting out because it's run after intent

                    // This is done to somehow run initViewPager()
                    Intent in = new Intent(context, MainActivity.class);
                    in.putExtra("doit", "importwebdav");
                    context.startActivity(in);
                }
            }
        }
        catch (Exception e)
        {
            System.out.println(e.toString());
            Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show();
        }
    }


    public static void checkWebdav(Context context, String webdavurl, String webdavusername, String webdavpassword)
    {
        // Enable networking on main thread
        StrictMode.ThreadPolicy gfgPolicy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(gfgPolicy);

        Sardine sardine = new OkHttpSardine();
        sardine.setCredentials(webdavusername, webdavpassword);

        try
        {
            List<DavResource> resources = sardine.list(webdavurl);

            for (DavResource res : resources)
            {
                System.out.println("Resources: " + res.getName());
            }

            Toast.makeText(context, "Connection Succesful", Toast.LENGTH_SHORT).show();

        }
        catch(Exception e)
        {
            Toast.makeText(context, "Connection Unsuccesful", Toast.LENGTH_SHORT).show();
        }

    }

    // Navigates to given activity based on the selected menu item
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item)
    {
        if(item.getItemId() == R.id.home)
        {
            Intent in = new Intent(this,MainActivity.class);
            startActivity(in);
            overridePendingTransition(0,0);
        }
        else if(item.getItemId() == R.id.exercises)
        {
            Intent in = new Intent(this,ExercisesActivity.class);
            startActivity(in);
            overridePendingTransition(0,0);
        }
        else if(item.getItemId() == R.id.diary)
        {
            Intent in = new Intent(this,DiaryActivity.class);
            in.putExtra("date", date_selected);
            startActivity(in);
            overridePendingTransition(0,0);
        }
        else if(item.getItemId() == R.id.charts)
        {
            Intent in = new Intent(this,ChartsActivity.class);
            startActivity(in);
            overridePendingTransition(0,0);
        }
        else if(item.getItemId() == R.id.me)
        {
            Intent in = new Intent(this,MeActivity.class);
            startActivity(in);
            overridePendingTransition(0,0);
        }
        return true;
    }

    // Menu Stuff
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item)
    {
        if(item.getItemId() == R.id.home)
        {
//            DialogFragment datePicker = new DatePickerFragment();
//            datePicker.show(getSupportFragmentManager(),"date picker");
            viewPager2.setCurrentItem((Infinite_Workout_Days.size()+1)/2); // Navigate to today
        }
        else if(item.getItemId() == R.id.settings)
        {
            Intent in = new Intent(this,SettingsActivity.class);
            startActivity(in);
        }
        return super.onOptionsItemSelected(item);
    }
}
