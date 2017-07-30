package com.eerovil.babysheets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import java.util.ArrayList;

public class ChartActivity extends AppCompatActivity {
    private ArrayList<MainActivity.MyListItem> listItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        listItems = savedInstanceState.getParcelable("listItems");
        setContentView(new CustomView(this));
    }

    public class CustomView extends View {

        private Rect rectangle;
        private Paint paint;

        public CustomView(Context context) {
            super(context);
            int x = 50;
            int y = 50;
            int sideLength = 200;

            // create a rectangle that we'll draw later
            rectangle = new Rect(x, y, sideLength, sideLength);

            // create the Paint and set its color
            paint = new Paint();
            paint.setColor(Color.GRAY);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Color.BLUE);
            canvas.drawRect(rectangle, paint);
        }

    }
}
