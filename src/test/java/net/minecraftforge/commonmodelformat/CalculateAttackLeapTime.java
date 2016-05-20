package net.minecraftforge.commonmodelformat;

import com.google.common.base.Supplier;

public class CalculateAttackLeapTime implements com.google.common.base.Supplier<Float>
{
    final float time;
    final float last_ai_trigger_time;

    public CalculateAttackLeapTime(float current_time, float last_ai_trigger_time)
    {
        this.time = current_time;
        this.last_ai_trigger_time = last_ai_trigger_time;
    }

    public static void main(String[] args) {
        Float result = new CalculateAttackLeapTime(10234.684f, 10232.65f).get();
        result = result;
    }

    public Float get() {
        return new com.google.common.base.Supplier<Float>() {

            //"#fps": 30,
            private float fps() {
                return 30;
            }
            //"#speed": 1,
            private float speed() {
                return 1;
            }
            //"#attack_leap": 70,
            private float attack_leap() {
                return 70; // Frame 70
            }
            //"#end": 96,
            private float end() {
                return 95; //End of animation
            }
            //"#mul": ["*", "#fps", "#speed"],
            private float mul() {
                return fps() * speed();  //Speed multiplier
            }
            //"#range": ["#lambda", ["#f", "#g"], ["-", "#g", "#f"]],
            private float range(float a, float b) {
                return b - a;
            }
            //"#since_click_start":      ["-*", "#time", "#click_start", "#mul"],
            private float since_ai_trigger_time() {
                return (time - last_ai_trigger_time) * mul();
            }
            //"#leap_length":  ["#range", "#attack_leap", "#end"],
            private float leap_length() {
                return range(attack_leap(), end());
            }
            //"#attack_leap_time": ["m+/", "#last_ai_trigger_time", "#leap_length", "#attack_leap", "#fps"],
            private float attack_leap_time()
            {
                return (Math.min(since_ai_trigger_time(), leap_length()) + attack_leap()) / fps();
            }

            public Float get()
            {
                return attack_leap_time();
            }
        }.get();
    }
}