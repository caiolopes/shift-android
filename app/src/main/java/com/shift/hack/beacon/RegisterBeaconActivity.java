package com.shift.hack.beacon;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.shift.hack.beacon.model.User;
import com.shift.hack.beacon.network.ApiClient;
import com.shift.hack.beacon.network.ServiceGenerator;
import com.skyfishjy.library.RippleBackground;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.io.IOException;
import java.util.Collection;

import de.hdodenhof.circleimageview.CircleImageView;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterBeaconActivity extends AppCompatActivity implements BeaconConsumer {
    protected static final String TAG = "MonitoringActivity";
    private BeaconManager beaconManager;

    private TextView textSearching;
    private User user = null;
    private boolean lock = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_beacon);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        user = User.getUser(this);

        if (user == null) {
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(intent);
            finish();
        }

        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.bind(this);

        textSearching = (TextView) findViewById(R.id.text_searching);

        CircleImageView imageView = (CircleImageView) findViewById(R.id.profile_image);

        //Glide.with(this).load("https://graph.facebook.com/" + user.getFbid() +
        //        "/picture?width=200&height=200&access_token=" + user.getToken()).into(imageView);

        ((RippleBackground)findViewById(R.id.ripple)).startRippleAnimation();

        getSupportActionBar().setTitle("Register Beacon");
    }

    private void loadBeacon(final String uuid) {
        Log.d("LOADBEACON", "LOADBEACON");
        ServiceGenerator.createService(ApiClient.class).getDevice(uuid).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, final Response<ResponseBody> response) {
                Log.d("onResponse", "LOADBEACON");
                if (response.body() != null) {

                    JsonArray ja;

                    try {
                        ja = new JsonParser().parse(response.body().string()).getAsJsonArray();
                    } catch (IOException e) {
                        e.printStackTrace();
                        onBackPressed();
                        return;
                    }

                    if (ja.size() == 0) {
                        storeBeacon(uuid);
                        return;
                    }

                    Toast.makeText(getApplicationContext(), "Beacon already registered!", Toast.LENGTH_SHORT).show();
                    onBackPressed();
                } else {
                    Log.d("ERROR", response.message());
                    storeBeacon(uuid);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.d("onFailure", t.getMessage());
                storeBeacon(uuid);
            }
        });
    }

    private void storeBeacon(final String uuid) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_beacon, null, false);

        final EditText name = (EditText) view.findViewById(R.id.name);
        final EditText value = (EditText) view.findViewById(R.id.value);

        AlertDialog alertDialog = new AlertDialog.Builder(this)
            .setTitle("Beacon Found")
            .setView(view)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    saveBeacon(uuid, name.getText().toString(), value.getText().toString());
                }
            })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    onBackPressed();
                }
            })
            .create();

        alertDialog.show();
    }

    private void saveBeacon(String uuid, String name, String value) {
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Creating, please wait...");
        progressDialog.setIndeterminate(true);
        progressDialog.show();

        ServiceGenerator.createService(ApiClient.class)
        .createBeacon(user.get_id(), uuid, name, value).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, final Response<ResponseBody> response) {
                Log.d("onResponse", "LOADBEACON");
                progressDialog.dismiss();
                if (response.body() != null) {
                    Toast.makeText(getApplicationContext(), "Beacon registered!", Toast.LENGTH_SHORT).show();
                    onBackPressed();
                } else {
                    Log.d("ERROR", response.message());
                    Toast.makeText(getApplicationContext(), "Failed to register beacon!", Toast.LENGTH_SHORT).show();
                    onBackPressed();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.d("onFailure", t.getMessage());
                progressDialog.dismiss();
                Toast.makeText(getApplicationContext(), "Failed to register beacon!", Toast.LENGTH_SHORT).show();
                onBackPressed();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
    }

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if (beacons.size() > 0) {
                    Log.i(TAG, "The first beacon I see is about " + beacons.iterator().next().getDistance() + " meters away.");

                    for (Beacon beacon : beacons) {
                        if (beacon.getDistance() < 0.05 && !lock) {
                            lock = true;
                            Log.d("LOADBEACON", beacon.getId1().toString());
                            loadBeacon(beacon.getId1().toString());
                        }
                    }
                }
            }
        });

        beaconManager.setMonitorNotifier(new MonitorNotifier() {
            @Override
            public void didEnterRegion(Region region) {
                Log.i(TAG, "I just saw an beacon for the first time!");
            }

            @Override
            public void didExitRegion(Region region) {
                Log.i(TAG, "I no longer see an beacon");
            }

            @Override
            public void didDetermineStateForRegion(int state, Region region) {
                Log.i(TAG, "I have just switched from seeing/not seeing beacons: " + state);
            }
        });

        try {
            beaconManager.startMonitoringBeaconsInRegion(new Region("myMonitoringUniqueId", null, null, null));
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {    }
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(getApplicationContext(), BeaconsActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
