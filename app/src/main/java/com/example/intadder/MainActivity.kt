package com.example.intadder

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.math.BigInteger

class MainActivity : AppCompatActivity() {
    companion object {
        const val KeySwitchState = "KEY_SWITCH"

        const val EditTextFileName = "edit.txt"
    }

    val integerRegex = Regex("""(\-?[0-9]+)""")
    val containsNumberRegex = Regex("""[\-0-9]""")
    var job: Deferred<String?>? = null

    // onCreateで初期化するWidgets
    lateinit var editText: EditText
    lateinit var sumView: TextView
    lateinit var calcSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // lateinit変数を初期化
        editText = findViewById(R.id.editText) as EditText
        sumView = findViewById(R.id.sumView) as TextView
        calcSwitch = findViewById(R.id.calcSwitch) as Switch

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (s != null) {
                    onEditTextChanged(s, start, count)
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null) {
                    onEditTextChanged(s, start, count)
                }
            }
        })

        // オンにしたときに合計値を表示
        calcSwitch.setOnCheckedChangeListener { _: CompoundButton, check: Boolean -> if (check) { showSum() } }
    }

    // メニュー
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.option, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.share -> {
                share()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()

        // スイッチとテキストの状態を読み込み
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val switchChecked = pref.getBoolean(KeySwitchState, true)
        calcSwitch.isChecked = switchChecked

        // 共有かチェック
        val action = intent.action
        if (Intent.ACTION_SEND.equals(action)) {
            // 共有から読み込む
            Log.v("myIntent", "received.")
            val s = intent.extras?.getCharSequence(Intent.EXTRA_TEXT)
            if (s != null) {
                Log.v("myIntent", "text: ${s}")
                editText.setText(s)
            }
        } else {
            // ファイルから読み込む
            val file = File(applicationContext.filesDir, EditTextFileName)
            if (file.exists()) {
                try {
                    editText.setText(file.bufferedReader().use(BufferedReader::readText))
                } catch (e: IOException) {
                    Log.e("Exception", "${e}")
                }
            }
        }

        // スイッチオンなら計算と表示
        if (switchChecked) {
            showSum()
        }
    }

    override fun onStop() {
        super.onStop()

        job?.cancel()
        job = null

        // スイッチとテキストの状態を保存
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = pref.edit()

        editor.clear()
        editor.putBoolean(KeySwitchState, calcSwitch.isChecked)

        editor.commit()

        val s = editText.text
        try {
            File(applicationContext.filesDir, EditTextFileName).printWriter().use {
                it.print(s)
            }
        } catch (e: IOException) {
            Log.e("Exception", "${e}")
        }
    }

    private fun isNumberOrMinus(c: Char): Boolean {
        // c.isDigit()は全角文字にもtrueを返すので使えない
        return (c in '0' .. '9') || c == '-'
    }

    fun onEditTextChanged(s: CharSequence, start: Int, count: Int) {
        if (!calcSwitch.isChecked) {
            // オフのときは合計値を消す
            sumView.text = ""
            return
        }

        if (start > 0 && start + count < s.length) {
            val before = s[start - 1]
            val after = s[start + count]

            // 編集部分の外側が両方とも数字かマイナス記号なら再計算
            if (isNumberOrMinus(before) && isNumberOrMinus(after)) {
                Log.d("textChanged", "${before}|${after}")
                showSum()
                return
            }
        }
        Log.d("textChanged", "${s.subSequence(start, start + count)}")

        val part = s.subSequence(start, start + count)
        if (containsNumberRegex.containsMatchIn(part)) {
            // 挿入または削除した部分に数字かマイナス記号が含まれている
            Log.d("textChanged", "matched [${part}]")
            showSum()
            return
        }
    }

    /// textView合計値を計算して表示
    private fun showSum() = GlobalScope.launch(Dispatchers.Main) {
        job?.cancel()

        val s = editText.text.toString()
        job = async(Dispatchers.Default) {
            var sum = BigInteger("0")

            try {
                for (e in integerRegex.findAll(s)) {
                    if (!isActive) {
                        Log.d("coroutine","Cancelled by isActive")
                        return@async null
                    }
                    sum = sum.add(BigInteger(e.value))
                }
            } catch (_: CancellationException) {
                Log.d("coroutine","Cancelled by exception")
                return@async null
            }

            if (!isActive) {
                Log.d("coroutine","Cancelled by isActive")
                return@async null
            }
            return@async "${sum}"
        }

        val result = job?.await()
        if (result != null) {
            Log.d("coroutine","result is ${result}. job is completed.")
            sumView.text = result
        }
    }

    private fun share() {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, sumView.text.toString())
        shareIntent.putExtra(Intent.EXTRA_TEXT, editText.text.toString())
        shareIntent.type = "text/plain"
        startActivity(shareIntent)
    }
}
