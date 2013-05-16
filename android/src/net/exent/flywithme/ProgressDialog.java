package net.exent.flywithme;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Service;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ProgressDialog extends DialogFragment {
    private AsyncTask<?, ?, ?> task;
    private View view;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Log.d(getClass().getName(), "onCreateDialog(" + savedInstanceState + ")");
        LayoutInflater inflater = getActivity().getLayoutInflater();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        view = inflater.inflate(R.layout.progress_dialog, null);
        builder.setView(view);
        if (task != null) {
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Log.d(getClass().getName(), "NegativeButton.onClick(" + dialog + ", " + id + "): task: " + task);
                    if (task != null && !task.isCancelled())
                        task.cancel(true);
                }
            }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    Log.d(getClass().getName(), "CancelListener.onCancel(" + dialog + "): task: " + task);
                    if (task != null && !task.isCancelled())
                        task.cancel(true);
                }
            });
        }
        return builder.create();
    }
    
    public String getInputText() {
        Log.d(getClass().getName(), "getInputText()");
        EditText progressInput = (EditText) view.findViewById(R.id.progressInput);
        return progressInput.getText().toString();
    }

    public void setTask(AsyncTask<?, ?, ?> task) {
        Log.d(getClass().getName(), "setTask(" + task + ")");
        this.task = task;
    }

    public void setProgress(int progress, String text) {
        Log.d(getClass().getName(), "setProgress(" + progress + ", " + text + ")");
        ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.progressBar);
        if (progressBar != null)
            progressBar.setProgress(progress > progressBar.getMax() ? progressBar.getMax() : progress);
        TextView progressText = (TextView) view.findViewById(R.id.progressText);
        if (progressText != null)
            progressText.setText(text);
    }
    
    public void setImage(Bitmap image) {
        Log.d(getClass().getName(), "setImage(" + image + ")");
        ImageView progressImage = (ImageView) view.findViewById(R.id.progressImage);
        progressImage.setImageBitmap(image);
        progressImage.setVisibility(View.VISIBLE);
    }
    
    public void showInput(final Runnable runnable) {
        Log.d(getClass().getName(), "showInput()");
        final EditText progressInput = (EditText) view.findViewById(R.id.progressInput);
        progressInput.setInputType(0x00001001); // set input to upper case
        progressInput.setVisibility(View.VISIBLE);
        progressInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                runnable.run();
                progressInput.setVisibility(View.GONE);
                ImageView progressImage = (ImageView) view.findViewById(R.id.progressImage);
                progressImage.setVisibility(View.GONE);
                return true;
            }
        });
        progressInput.requestFocus();
        ((InputMethodManager) getActivity().getSystemService(Service.INPUT_METHOD_SERVICE)).showSoftInput(progressInput, 0);
    }
}