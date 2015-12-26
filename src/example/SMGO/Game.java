package example.SMGO;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.view.*;
import android.view.animation.TranslateAnimation;
import android.widget.*;
import com.flurry.android.FlurryAgent;
import com.iPhoria.SMGO.*;
import com.iPhoria.SMGO.entity.Product;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import org.json.JSONException;

import java.util.List;

public class Game extends Activity implements Receiver {
    private ImageButton letsShopBtn;
    private ImageView dialer;
    private int dialerHeight, dialerWidth;
    private Matrix matrix;
    private Bitmap imageOriginal, imageScaled;
    private GestureDetector detector;
    // needed for detecting the inversed rotations
    private boolean[] quadrantTouched;
    private List<Product> gameProducts;
    private RelativeLayout productLayout;
    private Product prize;
    private TextView productPrice, goodChoice, tryAgain, productTitle;
    private ImageView productImgView;
    private ImageButton whereBtn;
    private ProgressBar productPbar;
    private LinearLayout tryAgainLayout;
    private DisplayImageOptions options;
    private ImageLoader imageLoader;
    private Typeface font;

    @Override
    protected void onStart() {
        super.onStart();
        FlurryAgent.onStartSession(this, ((MyApplication) this.getApplication()).getFlurryAPIKey());
    }

    @Override
    protected void onStop() {
        super.onStop();
        FlurryAgent.onEndSession(this);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        httpClient.initialize(this);
        super.onCreate(savedInstanceState);

        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        productLayout = (RelativeLayout) findViewById(R.id.couponGame);

        productImgView = (ImageView) findViewById(R.id.imageViewProductGame);
        productPbar = (ProgressBar) findViewById(R.id.pbarProductGame);
        productTitle = (TextView) findViewById(R.id.titleProductGame);
        productPrice = (TextView) findViewById(R.id.priceProductGame);
        goodChoice = (TextView) findViewById(R.id.goodChoiceProductGame);
        letsShopBtn = (ImageButton) findViewById(R.id.imageViewLetsShopGame);
        whereBtn = (ImageButton) findViewById(R.id.whereButtonGame);
        tryAgain = (TextView) findViewById(R.id.tryAgainGame);
        tryAgainLayout = (LinearLayout) findViewById(R.id.tryAgainLayoutGame);

        this.font = Typefaces.get(this.getBaseContext(), "HelveticaNeue.ttf");
        productTitle.setTypeface(font);
        productPrice.setTypeface(font);
        goodChoice.setTypeface(font);
        tryAgain.setTypeface(font);
        if (HTTPClient.isConnected()) {
            receiveGameProductsFromServer();
        } else {
            ShowConnectionFailMessage();
        }
        letsShopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Game.this, Categories.class);
                startActivity(intent);
            }
        });

        // load the image only once
        if (imageOriginal == null) {
            imageOriginal = BitmapFactory.decodeResource(getResources(), R.drawable.game__circle);
        }

        // initialize the matrix only once
        if (matrix == null) {
            matrix = new Matrix();
        } else {
            matrix.reset();
        }

        detector = new GestureDetector(this, new MyGestureDetector());
        // there is no 0th quadrant, to keep it simple the first value gets ignored
        quadrantTouched = new boolean[]{false, false, false, false, false};
        dialer = (ImageView) findViewById(R.id.game_circle);
        dialer.setOnTouchListener(new MyOnTouchListener());
        dialer.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                // method called more than once, but the values only need to be initialized one time
                if (dialerHeight == 0 || dialerWidth == 0) {
                    dialerHeight = dialer.getHeight();
                    dialerWidth = dialer.getWidth();

                    // resize
                    Matrix resize = new Matrix();
                    resize.postScale((float) Math.min(dialerWidth, dialerHeight) / (float) imageOriginal.getWidth(), (float) Math.min(dialerWidth, dialerHeight) / (float) imageOriginal.getHeight());
                    imageScaled = Bitmap.createBitmap(imageOriginal, 0, 0, imageOriginal.getWidth(), imageOriginal.getHeight(), resize, false);

                    // translate to the image view's center
                    float translateX = dialerWidth / 2 - imageScaled.getWidth() / 2;
                    float translateY = dialerHeight / 2 - imageScaled.getHeight() / 2;
                    matrix.postTranslate(translateX, translateY);

                    dialer.setImageBitmap(imageScaled);
                    dialer.setImageMatrix(matrix);
                }
            }
        });

    }

    private void receiveGameProductsFromServer() {
        if (HTTPClient.isConnected()) {
            new HTTPAsynchTask(parcer, this).execute(Responses.requestGameProducts());
        }
    }

    @Override
    public void receiveObjects() {
        try {
            objectHolder.setGameProducts(parcer.parceToProducts());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.gameProducts = objectHolder.getGameProducts();
        prize = getRandomPrize(gameProducts);
        tryAgainLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prize = getRandomPrize(gameProducts);
                TranslateAnimation animate = new TranslateAnimation(0, 0, 0, -productLayout.getHeight() * 1.5f);
                animate.setDuration(900);
                animate.setFillAfter(true);
                productLayout.startAnimation(animate);
                productLayout.setVisibility(View.INVISIBLE);
                letsShopBtn.setEnabled(true);
                dialer.setEnabled(true);
            }
        });
    }

    private Product getRandomPrize(List<Product> gameProducts){
        return gameProducts.size() > 0 ? gameProducts.get((int) Math.floor(Math.random() * gameProducts.size())) : null;
    }

    private void loadImageFromURL(String url) {
        options = new DisplayImageOptions.Builder()
                .showStubImage(R.color.noImage)
                .showImageForEmptyUri(R.color.noImage)
                .cacheInMemory()
                .imageScaleType(ImageScaleType.EXACTLY).cacheOnDisc(true)
                .build();

        imageLoader = ImageLoader.getInstance();
        imageLoader.displayImage(url, productImgView, options,
                new ImageLoadingListener() {
                    @Override
                    public void onLoadingCancelled(String s, View view) {
                    }

                    @Override
                    public void onLoadingComplete(String s, View v, Bitmap bitmap) {
                        Categories.setClickable(true);
                        productPbar.setVisibility(View.INVISIBLE);

                    }

                    @Override
                    public void onLoadingFailed(String s, View v, FailReason failReason) {
                        Categories.setClickable(true);
                        productPbar.setVisibility(View.INVISIBLE);
                    }

                    @Override
                    public void onLoadingStarted(String s, View v) {
                        Categories.setClickable(true);
                        productPbar.setVisibility(View.VISIBLE);
                    }
                }
        );

    }

    private class MyOnTouchListener implements View.OnTouchListener {

        private double startAngle;

        @Override
        public boolean onTouch(View v, MotionEvent event) {

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:

                    // reset the touched quadrants
                    for (int i = 0; i < quadrantTouched.length; i++) {
                        quadrantTouched[i] = false;
                    }

                    startAngle = getAngle(event.getX(), event.getY());
                    break;

                case MotionEvent.ACTION_MOVE:
                    double currentAngle = getAngle(event.getX(), event.getY());
                    rotateDialer((float) (startAngle - currentAngle));
                    startAngle = currentAngle;
                    break;

                case MotionEvent.ACTION_UP:
                    break;
            }

            // set the touched quadrant to true
            quadrantTouched[getQuadrant(event.getX() - (dialerWidth / 2), dialerHeight - event.getY() - (dialerHeight / 2))] = true;
            detector.onTouchEvent(event);
            return true;
        }
    }


    private class MyGestureDetector extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // get the quadrant of the start and the end of the fling
            int q1 = getQuadrant(e1.getX() - (dialerWidth / 2), dialerHeight - e1.getY() - (dialerHeight / 2));
            int q2 = getQuadrant(e2.getX() - (dialerWidth / 2), dialerHeight - e2.getY() - (dialerHeight / 2));

            // the inversed rotations
            if ((q1 == 2 && q2 == 2 && Math.abs(velocityX) < Math.abs(velocityY))
                    || (q1 == 3 && q2 == 3)
                    || (q1 == 1 && q2 == 3)
                    || (q1 == 4 && q2 == 4 && Math.abs(velocityX) > Math.abs(velocityY))
                    || ((q1 == 2 && q2 == 3) || (q1 == 3 && q2 == 2))
                    || ((q1 == 3 && q2 == 4) || (q1 == 4 && q2 == 3))
                    || (q1 == 2 && q2 == 4 && quadrantTouched[3])
                    || (q1 == 4 && q2 == 2 && quadrantTouched[3])) {

                dialer.post(new FlingRunnable(-1 * (velocityX + velocityY)));
            } else {
                // the normal rotation
                dialer.post(new FlingRunnable(velocityX + velocityY));
            }
            return true;
        }
    }

    private class FlingRunnable implements Runnable {

        private float velocity;
        private double decceleration;
        private final int time = 5;//seconds
        private final int framesPerSecond = 50;
        private final int oneFrameTime = 1000 / framesPerSecond;

        public FlingRunnable(float velocity) {
            this.velocity = velocity;
            this.decceleration = Math.pow(1 / Math.abs(velocity), 1d / (time * framesPerSecond));
        }

        @Override
        public void run() {
            long before = System.currentTimeMillis();

            if (Math.abs(velocity) > 5) {
                letsShopBtn.setEnabled(false);
                dialer.setEnabled(false);
                rotateDialer(velocity / 75);
                velocity *= decceleration;
                // post this instance again
                dialer.post(this);
            } else {
                velocity = 0;
                if (prize != null) {
                    letsShopBtn.setEnabled(false);
                    dialer.setEnabled(false);
                    productTitle.setText(Html.fromHtml(prize.getName()));
                    productPrice.setText(getProductPrice());

                    loadImageFromURL(prize.getPath());
                    whereBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            letsShopBtn.performClick();
                            Bundle bundle = new Bundle();
                            bundle.putInt("shopId", Integer.valueOf(prize.getShopId()));
                            Categories.setInitTab(1, bundle);
                        }
                    });
                    TranslateAnimation animate = new TranslateAnimation(0, 0, -productLayout.getHeight(), 0);
                    animate.setDuration(900);
                    animate.setFillAfter(true);
                    productLayout.setVisibility(View.VISIBLE);
                    productLayout.startAnimation(animate);
                } else {
                    letsShopBtn.setEnabled(true);
                    dialer.setEnabled(true);
                }
            }
            long after = System.currentTimeMillis();
            long diff = after - before;
            if (oneFrameTime > diff) {
                try {
                    Thread.sleep(oneFrameTime - diff);//one frame time - work time
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private String getProductPrice() {
            if (prize.getPrice().contains("Call") || prize.getPrice().contains("манат")) {
                return prize.getPrice();
            } else {
                return prize.getPrice() + " DTM";
            }
        }
    }

    /**
     * @return The angle of the unit circle with the image view's center
     */
    private double getAngle(double xTouch, double yTouch) {
        double x = xTouch - (dialerWidth / 2d);
        double y = dialerHeight - yTouch - (dialerHeight / 2d);

        switch (getQuadrant(x, y)) {
            case 1:
                return Math.asin(y / Math.hypot(x, y)) * 180 / Math.PI;
            case 2:
                return 180 - Math.asin(y / Math.hypot(x, y)) * 180 / Math.PI;
            case 3:
                return 180 + (-1 * Math.asin(y / Math.hypot(x, y)) * 180 / Math.PI);
            case 4:
                return 360 + Math.asin(y / Math.hypot(x, y)) * 180 / Math.PI;
            default:
                return 0;
        }
    }

    /**
     * @return The selected quadrant.
     */
    private static int getQuadrant(double x, double y) {
        if (x >= 0) {
            return y >= 0 ? 1 : 4;
        } else {
            return y >= 0 ? 2 : 3;
        }
    }

    /**
     * Rotate the dialer.
     *
     * @param degrees The degrees, the dialer should get rotated.
     */
    private void rotateDialer(float degrees) {
        matrix.postRotate(degrees, dialerWidth / 2, dialerHeight / 2);
        dialer.setImageMatrix(matrix);
    }

    private void ShowConnectionFailMessage() {
        AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
        dlgAlert.setTitle("Error!");
        dlgAlert.setMessage("Connection is not avialable!\nTry again?");
        dlgAlert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                if (HTTPClient.isConnected()) {
                    receiveGameProductsFromServer();
                } else {
                    ShowConnectionFailMessage();
                }
            }
        });
        dlgAlert.setCancelable(true);
        dlgAlert.create().show();
    }
}
