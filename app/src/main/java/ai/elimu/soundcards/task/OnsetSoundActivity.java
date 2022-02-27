package ai.elimu.soundcards.task;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;

import ai.elimu.model.v2.gson.content.AudioGson;
import ai.elimu.model.v2.gson.content.LetterGson;
import ai.elimu.model.v2.gson.content.SoundGson;
import ai.elimu.model.v2.gson.content.WordGson;
import ai.elimu.soundcards.R;
import ai.elimu.soundcards.util.IpaToAndroidResourceConverter;
import ai.elimu.soundcards.util.MediaPlayerHelper;
import ai.elimu.soundcards.util.TtsHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OnsetSoundActivity extends AppCompatActivity {

    private List<WordGson> wordsWithImage;

    private List<WordGson> wordsCorrectlySelected;

    private ImageView emojiImageView;

    private ProgressBar progressBar;

    private CardView alt1CardView;
    private ImageView alt1ImageView;

    private CardView alt2CardView;
    private ImageView alt2ImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(getClass().getName(), "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_onset_sound);

        wordsCorrectlySelected = new ArrayList<>();

        emojiImageView = (ImageView) findViewById(R.id.emoji);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        alt1CardView = (CardView) findViewById(R.id.alt1CardView);
        alt1ImageView = (ImageView) findViewById(R.id.alt1ImageView);

        alt2CardView = (CardView) findViewById(R.id.alt2CardView);
        alt2ImageView = (ImageView) findViewById(R.id.alt2ImageView);

        // Fetch words starting with the sound of one of the unlocked letters
        List<LetterGson> unlockedLetters = ContentProvider.getUnlockedLetters();
        List<String> soundStrings = new ArrayList<>();
        for (LetterGson unlockedLetter : unlockedLetters) {
            String soundAsString = "";
            for (SoundGson sound : unlockedLetter.getSounds()) {
                soundAsString += sound.getValueIpa();
            }
            soundStrings.add(soundAsString);
        }
        Log.i(getClass().getName(), "soundStrings: " + soundStrings);
        WordDao wordDao = ContentProvider.getDaoSession().getWordDao();
        String query = ""; // See http://greenrobot.org/greendao/documentation/queries/#Raw_queries
        for (int i = 0; i < soundStrings.size(); i++) {
            String unlockedLetterSound = soundStrings.get(i);
            if (i == 0) {
                query = "WHERE T.PHONETICS LIKE \"" + unlockedLetterSound + "%\"";
            } else {
                query += " OR T.PHONETICS LIKE \"" + unlockedLetterSound + "%\"";
            }
        }
        query += " ORDER BY T.USAGE_COUNT DESC";
        Log.i(getClass().getName(), "query: " + query);
        List<WordGson> wordsStartingWithUnlockedLetterSound = wordDao.queryRaw(query);
        Log.i(getClass().getName(), "wordsStartingWithUnlockedLetterSound.size(): " + wordsStartingWithUnlockedLetterSound.size());
        for (WordGson word : wordsStartingWithUnlockedLetterSound) {
            Log.i(getClass().getName(), "word.getText(): " + word.getText() + ", word.getPhonetics(): " + word.getPhonetics() + ", word.getUsageCount(): " + word.getUsageCount());
        }

        // Fetch 10 most frequent words with a corresponding image
        wordsWithImage = new ArrayList<>();
        for (WordGson word : wordsStartingWithUnlockedLetterSound) {
            Log.i(getClass().getName(), "word.getPhonetics(): " + word.getPhonetics());

            AudioGson matchingAudio = ContentProvider.getAudio(word.getText());
            List<Image> matchingImages = ContentProvider.getAllImagesLabeledByWord(word);

            // TODO: add audio as requirement
            if (/*(matchingAudio != null) &&*/ !matchingImages.isEmpty()) {
                Log.i(getClass().getName(), "Adding \"" + word.getText() + "\"...");
                wordsWithImage.add(word);
            }
            if (wordsWithImage.size() == 10) {
                break;
            }
        }
        Log.i(getClass().getName(), "wordsWithImage.size(): " + wordsWithImage.size());
    }

    @Override
    protected void onStart() {
        Log.i(getClass().getName(), "onStart");
        super.onStart();

        // Animate subtle head movements
        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(emojiImageView, "rotation", 2);
        objectAnimator.setDuration(1000);
        objectAnimator.setRepeatCount(ValueAnimator.INFINITE);
        objectAnimator.setRepeatMode(ValueAnimator.REVERSE);
        objectAnimator.start();

        loadNextTask();
    }

    private void loadNextTask() {
        Log.i(getClass().getName(), "loadNextTask");

        if (wordsCorrectlySelected.size() == wordsWithImage.size()) {
            // TODO: show congratulations page
            finish();
            return;
        }

        int progress = (wordsCorrectlySelected.size() * 100) / wordsWithImage.size();
        ObjectAnimator objectAnimator = ObjectAnimator.ofInt(progressBar, "progress", progress);
        objectAnimator.setDuration(1000);
        objectAnimator.start();

        final WordGson alt1Word = wordsWithImage.get(wordsCorrectlySelected.size());
        Log.i(getClass().getName(), "alt1Word.getText(): " + alt1Word.getText());

        List<Image> alt1Images = ContentProvider.getAllImagesLabeledByWord(alt1Word);
        final Image alt1Image = alt1Images.get((int) (Math.random() * alt1Images.size()));
        File alt1ImageFile = MultimediaHelper.getFile(alt1Image);
        Bitmap alt1ImageBitmap = BitmapFactory.decodeFile(alt1ImageFile.getAbsolutePath());
        final String alt1ImageDominantColor = alt1Image.getDominantColor();
        final int alt1ColorIdentifier = parseRgbColor(alt1ImageDominantColor, 20);

        // TODO: fetch words with different onset sound
        List<WordGson> otherWords = new ArrayList<>(wordsWithImage);
        otherWords.remove(alt1Word);
        final WordGson alt2Word = otherWords.get((int) (Math.random() * otherWords.size()));
        Log.i(getClass().getName(), "alt2Word.getText(): " + alt2Word.getText());

        List<Image> alt2Images = ContentProvider.getAllImagesLabeledByWord(alt2Word);
        final Image alt2Image = alt2Images.get((int) (Math.random() * alt2Images.size()));
        File alt2ImageFile = MultimediaHelper.getFile(alt2Image);
        Bitmap alt2ImageBitmap = BitmapFactory.decodeFile(alt2ImageFile.getAbsolutePath());
        final String alt2ImageDominantColor = alt2Image.getDominantColor();
        final int alt2ColorIdentifier = parseRgbColor(alt2ImageDominantColor, 20);

        final boolean isAlt1Correct = (Math.random() > .5);
        if (isAlt1Correct) {
            alt1CardView.setCardBackgroundColor(alt1ColorIdentifier);
            alt1ImageView.setImageBitmap(alt1ImageBitmap);
            alt2CardView.setCardBackgroundColor(alt2ColorIdentifier);
            alt2ImageView.setImageBitmap(alt2ImageBitmap);
        } else {
            alt1CardView.setCardBackgroundColor(alt2ColorIdentifier);
            alt1ImageView.setImageBitmap(alt2ImageBitmap);
            alt2CardView.setCardBackgroundColor(alt1ColorIdentifier);
            alt2ImageView.setImageBitmap(alt1ImageBitmap);
        }

        Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.anim_swing_up_left);
        alt1CardView.startAnimation(animation);
        alt2CardView.startAnimation(animation);
        MediaPlayerHelper.play(getApplicationContext(), R.raw.shuffle_cards);

        alt1CardView.postDelayed(new Runnable() {
            @Override
            public void run() {
                MediaPlayerHelper.play(getApplicationContext(), R.raw.which_word_begins_with_this_sound);

                Log.i(getClass().getName(), "alt1Word.getPhonetics(): /" + alt1Word.getPhonetics() + "/");

                final String soundIpa = alt1Word.getPhonetics().substring(0, 1); // TODO: Handle case where Letter consists of more than 1 Sound (e.g. /ks/)
                Log.i(getClass().getName(), "soundIpa: " + soundIpa);

                final String androidResourceName = IpaToAndroidResourceConverter.getAndroidResourceName(soundIpa);
                Log.i(getClass().getName(), "androidResourceName: " + androidResourceName);

                int pauseBeforePlayingSound = 2500;
                if ("sw".equals(Locale.getDefault().getLanguage())) {
                    pauseBeforePlayingSound = 5000;
                }
                alt1CardView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        playSound(soundIpa);

                        Log.i(getClass().getName(), "Looking up resource: animated_emoji_u1f603_mouth_" + androidResourceName);
                        int drawableResourceId = getResources().getIdentifier("animated_emoji_u1f603_mouth_" + androidResourceName, "drawable", getPackageName());
                        try {
                            final Drawable drawable = getDrawable(drawableResourceId);
                            emojiImageView.setImageDrawable(drawable);
                            AnimatedVectorDrawable animatedVectorDrawable = (AnimatedVectorDrawable) emojiImageView.getDrawable();
                            animatedVectorDrawable.start();
                        } catch (Resources.NotFoundException e) {
                            Log.w(getClass().getName(), null, e);
                        }

                        alt1CardView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (isAlt1Correct) {
                                    playWord(alt1Word);
                                    ObjectAnimator animator = ObjectAnimator.ofFloat(alt1CardView, "cardElevation", alt1CardView.getCardElevation(), alt1CardView.getCardElevation() + 10, alt1CardView.getCardElevation());
                                    animator.setDuration(1000);
                                    animator.start();
                                } else {
                                    playWord(alt2Word);
                                    ObjectAnimator animator = ObjectAnimator.ofFloat(alt2CardView, "cardElevation", alt2CardView.getCardElevation(), alt2CardView.getCardElevation() + 10, alt2CardView.getCardElevation());
                                    animator.setDuration(1000);
                                    animator.start();
                                }

                                alt1CardView.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (isAlt1Correct) {
                                            playWord(alt2Word);
                                            ObjectAnimator animator = ObjectAnimator.ofFloat(alt2CardView, "cardElevation", alt2CardView.getCardElevation(), alt2CardView.getCardElevation() + 10, alt2CardView.getCardElevation());
                                            animator.setDuration(1000);
                                            animator.start();
                                        } else {
                                            playWord(alt1Word);
                                            ObjectAnimator animator = ObjectAnimator.ofFloat(alt1CardView, "cardElevation", alt1CardView.getCardElevation(), alt1CardView.getCardElevation() + 10, alt1CardView.getCardElevation());
                                            animator.setDuration(1000);
                                            animator.start();
                                        }

                                        if (isAlt1Correct) {
                                            alt1CardView.setOnClickListener(new View.OnClickListener() {
                                                @Override
                                                public void onClick(View v) {
                                                    Log.i(getClass().getName(), "alt1CardView onClick");

                                                    MediaPlayerHelper.play(getApplicationContext(), R.raw.alternative_correct);
                                                    int alt1ColorIdentifierDarker = parseRgbColor(alt1ImageDominantColor, 100);
                                                    alt1CardView.setCardBackgroundColor(alt1ColorIdentifierDarker);

                                                    wordsCorrectlySelected.add(alt1Word);

                                                    alt1CardView.setOnClickListener(null);

                                                    alt1ImageView.postDelayed(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.anim_disappear_left);
                                                            alt1CardView.startAnimation(animation);
                                                            alt2CardView.startAnimation(animation);
                                                            MediaPlayerHelper.play(getApplicationContext(), R.raw.swipe_cards);

                                                            loadNextTask();
                                                        }
                                                    }, 2000);
                                                }
                                            });

                                            alt2CardView.setOnClickListener(new View.OnClickListener() {
                                                @Override
                                                public void onClick(View v) {
                                                    Log.i(getClass().getName(), "alt2CardView onClick");

                                                    MediaPlayerHelper.play(getApplicationContext(), R.raw.alternative_incorrect);

                                                    Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.anim_shake);
                                                    alt2CardView.startAnimation(animation);
                                                }
                                            });
                                        } else {
                                            alt2CardView.setOnClickListener(new View.OnClickListener() {
                                                @Override
                                                public void onClick(View v) {
                                                    Log.i(getClass().getName(), "alt2CardView onClick");

                                                    MediaPlayerHelper.play(getApplicationContext(), R.raw.alternative_correct);
                                                    int alt2ColorIdentifierDarker = parseRgbColor(alt2ImageDominantColor, 100);
                                                    alt2CardView.setCardBackgroundColor(alt2ColorIdentifierDarker);

                                                    wordsCorrectlySelected.add(alt2Word);

                                                    alt1CardView.setOnClickListener(null);

                                                    alt1ImageView.postDelayed(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.anim_disappear_left);
                                                            alt1CardView.startAnimation(animation);
                                                            alt2CardView.startAnimation(animation);
                                                            MediaPlayerHelper.play(getApplicationContext(), R.raw.swipe_cards);

                                                            loadNextTask();
                                                        }
                                                    }, 2000);
                                                }
                                            });

                                            alt1CardView.setOnClickListener(new View.OnClickListener() {
                                                @Override
                                                public void onClick(View v) {
                                                    Log.i(getClass().getName(), "alt1CardView onClick");

                                                    MediaPlayerHelper.play(getApplicationContext(), R.raw.alternative_incorrect);

                                                    Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.anim_shake);
                                                    alt1CardView.startAnimation(animation);
                                                }
                                            });
                                        }
                                    }
                                }, 2000);
                            }
                        }, 2000);
                    }
                }, pauseBeforePlayingSound);
            }
        }, 1000);
    }

    private int parseRgbColor(String input, int alpha) {
        Pattern pattern = Pattern.compile("rgb *\\( *([0-9]+), *([0-9]+), *([0-9]+) *\\)");
        Matcher matcher = pattern.matcher(input);
        if (matcher.matches()) {
            int rgbRed = Integer.valueOf(matcher.group(1));
            int rgbGreen = Integer.valueOf(matcher.group(2));
            int rgbBlue = Integer.valueOf(matcher.group(3));
            int colorIdentifier = Color.argb(alpha, rgbRed, rgbGreen, rgbBlue);
            return colorIdentifier;
        } else {
            return -1;
        }
    }

    private void playSound(String ipaValue) {
        Log.i(getClass().getName(), "playSound");

        // Look up corresponding Audio
        Log.d(getClass().getName(), "Looking up \"sound_" + ipaValue + "\"");
        Audio audio = ContentProvider.getAudio("sound_" + ipaValue);
        Log.i(getClass().getName(), "audio: " + audio);
        if (audio != null) {
            // Play audio
            File audioFile = MultimediaHelper.getFile(audio);
            Uri uri = Uri.parse(audioFile.getAbsolutePath());
            MediaPlayer mediaPlayer = MediaPlayer.create(getApplicationContext(), uri);
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    Log.i(getClass().getName(), "mediaPlayer onCompletion");
                    mediaPlayer.release();
                }
            });
            mediaPlayer.start();
        } else {
            // Audio not found. Fall-back to application resource.
            String audioFileName = "sound_" + ipaValue;
            int resourceId = getResources().getIdentifier(audioFileName, "raw", getPackageName());
            try {
                if (resourceId != 0) {
                    MediaPlayerHelper.play(getApplicationContext(), resourceId);
                } else {
                    // Fall-back to TTS
                    TtsHelper.speak(getApplicationContext(), ipaValue);
                }
            } catch (Resources.NotFoundException e) {
                // Fall-back to TTS
                TtsHelper.speak(getApplicationContext(), ipaValue);
            }
        }
    }

    private void playWord(WordGson word) {
        Log.i(getClass().getName(), "playWord");

        // Look up corresponding Audio recording
        Log.d(getClass().getName(), "Looking up \"" + word.getText() + "\"");
        AudioGson audio = ContentProvider.getAudio(word.getText());
        Log.i(getClass().getName(), "audio: " + audio);
        if (audio != null) {
            // Play audio
            File audioFile = MultimediaHelper.getFile(audio);
            Uri uri = Uri.parse(audioFile.getAbsolutePath());
            MediaPlayer mediaPlayer = MediaPlayer.create(getApplicationContext(), uri);
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    Log.i(getClass().getName(), "onCompletion");
                    mediaPlayer.release();
                }
            });
            mediaPlayer.start();
        } else {
            // Audio recording not found. Fall-back to TTS.
            TtsHelper.speak(getApplicationContext(), word.getText());
        }
    }
}
