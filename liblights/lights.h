#ifndef FANCY_NOTIFICATION
char const*const RED_LED_FILE 			= "/sys/class/leds/red/brightness";
char const*const GREEN_LED_FILE 		= "/sys/class/leds/green/brightness";
char const*const BLUE_LED_FILE 			= "/sys/class/leds/blue/brightness";

char const*const RED_LED_FILE_TRIGGER		= "/sys/class/leds/red/trigger";
char const*const GREEN_LED_FILE_TRIGGER		= "/sys/class/leds/green/trigger";
char const*const BLUE_LED_FILE_TRIGGER		= "/sys/class/leds/blue/trigger";

char const*const RED_LED_FILE_DELAYON		= "/sys/class/leds/red/delay_on";
char const*const GREEN_LED_FILE_DELAYON		= "/sys/class/leds/green/delay_on";
char const*const BLUE_LED_FILE_DELAYON		= "/sys/class/leds/blue/delay_on";

char const*const RED_LED_FILE_DELAYOFF		= "/sys/class/leds/red/delay_off";
char const*const GREEN_LED_FILE_DELAYOFF	= "/sys/class/leds/green/delay_off";
char const*const BLUE_LED_FILE_DELAYOFF		= "/sys/class/leds/blue/delay_off";

char const*const BUTTON_BACKLIGHT_FILE[] = {
  "/sys/class/leds/so34-led0/brightness",
  "/sys/class/leds/so34-led1/brightness",
  "/sys/class/leds/so34-led2/brightness",
};

#else
char const*const RED_LED_FILE[] = {
 "/sys/class/leds/l-key-red/brightness";
 "/sys/class/leds/m-key-red/brightness";
 "/sys/class/leds/r-key-red/brightness";
 "/sys/class/leds/pwr-red/brightness";
}
char const*const GREEN_LED_FILE[]= {
 "/sys/class/leds/l-key-green/brightness";
 "/sys/class/leds/m-key-green/brightness";
 "/sys/class/leds/r-key-green/brightness";
 "/sys/class/leds/pwr-green/brightness";
}
char const*const BLUE_LED_FILE[] = {
 "/sys/class/leds/l-key-blue/brightness";
 "/sys/class/leds/m-key-blue/brightness";
 "/sys/class/leds/r-key-blue/brightness";
 "/sys/class/leds/pwr-blue/brightness";
}
char const*const RED_LED_FILE_TRIGGER		= "/sys/class/leds/red/trigger";
char const*const GREEN_LED_FILE_TRIGGER		= "/sys/class/leds/green/trigger";
char const*const BLUE_LED_FILE_TRIGGER		= "/sys/class/leds/blue/trigger";

char const*const RED_LED_FILE_DELAYON		= "/sys/class/leds/red/delay_on";
char const*const GREEN_LED_FILE_DELAYON		= "/sys/class/leds/green/delay_on";
char const*const BLUE_LED_FILE_DELAYON		= "/sys/class/leds/blue/delay_on";

char const*const RED_LED_FILE_DELAYOFF		= "/sys/class/leds/red/delay_off";
char const*const GREEN_LED_FILE_DELAYOFF	= "/sys/class/leds/green/delay_off";
char const*const BLUE_LED_FILE_DELAYOFF		= "/sys/class/leds/blue/delay_off";

char const*const BUTTON_BACKLIGHT_FILE[] = {
 "/sys/class/leds/l-key-red/brightness";
 "/sys/class/leds/m-key-red/brightness";
 "/sys/class/leds/r-key-red/brightness";
 "/sys/class/leds/l-key-green/brightness";
 "/sys/class/leds/m-key-green/brightness";
 "/sys/class/leds/r-key-green/brightness";
 "/sys/class/leds/l-key-blue/brightness";
 "/sys/class/leds/m-key-blue/brightness";
 "/sys/class/leds/r-key-blue/brightness";
};
#endif


char const*const LCD_BACKLIGHT_FILE		= "/sys/class/leds/lcd-backlight/brightness";

char const*const ALS_FILE			= "/sys/class/leds/lcd-backlight/device/als_enable";
