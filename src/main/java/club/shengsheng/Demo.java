package club.shengsheng;

/**
 * @author gongxuanzhangmelt@gmail.com
 **/
public class Demo {
    public static void main(String[] args) {
        TimerWheel timerWheel = new TimerWheel();
        for (int i = 0; i < 100; i++) {
            final int fi = i;
            timerWheel.addDelayTask(() -> {
                System.out.println(fi);
            }, 1000L * i);
        }
       
    }
}
