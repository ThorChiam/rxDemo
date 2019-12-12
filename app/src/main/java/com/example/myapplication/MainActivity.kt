package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*

class MainActivity : AppCompatActivity() {

    private var result: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        bt_test.setOnClickListener {
            RxDemo.startWithResult(result)
                .doOnNext {
                    Log.i(RxDemo.TAG, "Main In Progress:${it}")
                }
                .doOnCompleted {
                    Log.i(RxDemo.TAG, "result:completed!")
                }
                .doOnError {
                    Log.e(RxDemo.TAG, "Main - onError:${it}")
                }
                .subscribe()
        }

        bt_reset.setOnClickListener {
            result = ""
            RxDemo.reset()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    // multiple checkbox click method
    fun onCheckboxClicked(view: View) {
        var checkBoxView = view as CheckBox
        if (cb_shared == checkBoxView) {
            result += if (cb_shared.isChecked) "s" else ""
        }
        if (cb_flutter == checkBoxView) {
            result += if (cb_flutter.isChecked) "f" else ""
        }
        if (cb_pack == checkBoxView) {
            result += if (cb_pack.isChecked) "p" else ""
        }
    }
}
