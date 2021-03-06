package ca.uottawa.service4u;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Layout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class AvailabilityActivity extends AppCompatActivity {

    private static final SimpleDateFormat datetimeFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm");
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");

    private static final String dbTAG = "Database";

    protected FirebaseAuth mAuth;
    protected FirebaseDatabase database;
    protected DatabaseReference databaseUsers;

    int year;
    int month;
    int dayOfMonth;
    String dateString = null;

    ListView listTimeSlots;
    List<String> timeSlots;

    List<TimeInterval> myAvailability;
    List<TimeInterval> myBooked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_availability);

        Intent myIntent = getIntent(); // gets the previously created intent
        year = myIntent.getIntExtra("year",1);
        month= myIntent.getIntExtra("month",0);
        dayOfMonth= myIntent.getIntExtra("dayOfMonth",1);

        try {
            dateString = dateFormat.format(dateFormat.parse(String.format("%s-%s-%s",dayOfMonth, month, year)));
        } catch (ParseException e) {
            e.printStackTrace();
        }

        setTitle(dateString);

        timeSlots = Arrays.asList(getResources().getStringArray(R.array.time_slots));

        listTimeSlots = (ListView) findViewById(R.id.listTimeSlots);

        mAuth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance();
        databaseUsers = database.getReference("Users");

        FirebaseUser user = mAuth.getCurrentUser();
        myAvailability = new ArrayList<TimeInterval>();

        if (user != null) {
            //get userType
            databaseUsers.child(user.getUid()).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    // This method is called once with the initial value and again
                    // whenever data at this location is updated.
                    ServiceProvider appUser = dataSnapshot.getValue(ServiceProvider.class);

                    if (appUser.availability != null) {
                        myAvailability = appUser.availability;
                    } else {
                        myAvailability = new ArrayList<TimeInterval>();
                    }
                    Log.d(dbTAG, "Availability: " + myAvailability);

                    if (appUser.booked != null) {
                        myBooked = appUser.booked;
                    } else {
                        myBooked = new ArrayList<TimeInterval>();
                    }
                    Log.d(dbTAG, "Booked: " + myBooked);

                    updateForm();

                }

                @Override
                public void onCancelled(DatabaseError error) {
                    // Failed to read value
                    Log.e(dbTAG, "Failed to read value.", error.toException());
                }
            });

        }

        updateForm();



    }


    private void updateForm(){
        Log.d("Form", "Updating form");
        TimeSlotList timeSlotAdapter = new TimeSlotList(AvailabilityActivity.this, timeSlots, myAvailability, myBooked, dateString);
        listTimeSlots.setAdapter(timeSlotAdapter);

    }


    public void setAvailability(View view){
        String dtStr;
        long start = 0;
        long dt;
        boolean a = false; //available

        //clear current date
        for (Iterator<TimeInterval> iterator = myAvailability.iterator(); iterator.hasNext();){
            TimeInterval ti = iterator.next();
            if (ti != null) {
                if (dateFormat.format(ti.start).equals(dateString)) {
                    iterator.remove();
                }
            }
        }

            //add availability for current date
        for (int i = 0; i < listTimeSlots.getChildCount(); i++) {
            RelativeLayout ts = (RelativeLayout) listTimeSlots.getChildAt(i);
            CheckBox cb = (CheckBox) ts.getChildAt(1);

            dtStr = String.format("%s %s", dateString, timeSlots.get(i));
            try {
                dt = datetimeFormat.parse(dtStr).getTime();


                if (cb.isChecked() && !a){
                    start = dt;
                    a = true;

                } else if (!cb.isChecked() && a) {
                    a = false;
                    myAvailability.add(new TimeInterval(start,dt));

                }

            } catch (ParseException e) {
                e.printStackTrace();
            }

        }

        if (a) {
            a = false;

            dtStr = String.format("%s %s", dateString, timeSlots.get(listTimeSlots.getChildCount()));

            try {
                dt = datetimeFormat.parse(dtStr).getTime();

                myAvailability.add(new TimeInterval(start,dt));

            } catch (ParseException e) {
                e.printStackTrace();
            }

        }

        Collections.sort(myAvailability, (TimeInterval ti1, TimeInterval ti2) -> (Long.compare(ti1.start, ti2.start)));

        Log.v("dbTAG", "Setting availability: " + myAvailability.toString());

        FirebaseUser user = mAuth.getCurrentUser();
        databaseUsers.child(user.getUid()).child("availability").setValue(myAvailability);

        finish();
    }

    public class TimeSlotList extends ArrayAdapter<String> {


        private Activity context;
        private List<String> timeSlots;
        private List<TimeInterval> availability;
        private List<TimeInterval> booked;
        private String dateString;
        private List<String> timeIntervals;


        public TimeSlotList(Activity context, List<String> timeSlots, List<TimeInterval> availability, List<TimeInterval> booked, String dateString) {
            super(context, R.layout.layout_timeslot_list, timeSlots.subList(0,timeSlots.size()-1));
            this.context = context;
            this.timeSlots = timeSlots;
            this.availability = availability;
            this.booked = booked;
            this.dateString = dateString;

            timeIntervals = new ArrayList<String>();
            for (int i = 0; i < this.timeSlots.size()-1; i++){
                timeIntervals.add(this.timeSlots.get(i) + " - " + this.timeSlots.get(i+1));
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = context.getLayoutInflater();
            View listViewItem = inflater.inflate(R.layout.layout_timeslot_list, null, true);

            TextView timeSlotText = (TextView) listViewItem.findViewById(R.id.timeSlotText);
            String timeInterval = timeIntervals.get(position);
            timeSlotText.setText(timeInterval);

            CheckBox cb = (CheckBox) listViewItem.findViewById(R.id.timeSlotCheckBox);
            long dt;
            Date date;

            String dtStr = String.format("%s %s", dateString, timeSlots.get(position));
            try{
                date = datetimeFormat.parse(dtStr);
                dt = date.getTime();

                cb.setChecked(false);

                for (TimeInterval ti : availability){
                    if (ti.contains(dt)){
                        //Log.v("Form", "available " + ti);
                        cb.setChecked(true);
                        cb.setEnabled(true);
                    }
                }

                //don't allow editing of availability for times aready booked
                for (TimeInterval ti : booked){
                    if (ti.contains(dt)){
                        //Log.v("Form", "booked " + ti);
                        cb.setChecked(true);
                        cb.setEnabled(false);
                    }
                }

                //cannot change availability for past
                if (date.before(new Date())){
                    cb.setEnabled(false);
                }

            } catch (Exception e){
                Log.e("parser", e.getMessage());
            }

            return listViewItem;
        }


    }

}
