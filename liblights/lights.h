#ifndef FANCY_NOTIFICATION
char const*const RED_LED_FILE[] = {
 "/sys/class/leds/red/brightness",
};
char const*const GREEN_LED_FILE[] = {
 "/sys/class/leds/green/brightness",
};
char const*const BLUE_LED_FILE[] = {
 "/sys/class/leds/blue/brightness",
};

char const*const RED_LED_FILE_TRIGGER[] = {
 "/sys/class/leds/red/trigger",
};
char const*const GREEN_LED_FILE_TRIGGER[] = {
 "/sys/class/leds/green/trigger",
};
char const*const BLUE_LED_FILE_TRIGGER[] = {
 "/sys/class/leds/blue/trigger",
};

char const*const RED_LED_FILE_DELAYON[] = {
 "/sys/class/leds/red/delay_on",
};
char const*const GREEN_LED_FILE_DELAYON[] = {
 "/sys/class/leds/green/delay_on",
};
char const*const BLUE_LED_FILE_DELAYON[] = {
 "/sys/class/leds/blue/delay_on",
};

char const*const RED_LED_FILE_DELAYOFF[] = {
 "/sys/class/leds/red/delay_off",
};
char const*const GREEN_LED_FILE_DELAYOFF[] = {
 "/sys/class/leds/green/delay_off",
};
char const*const BLUE_LED_FILE_DELAYOFF[] = {
 "/sys/class/leds/blue/delay_off",
};

char const*const BUTTON_BACKLIGHT_FILE[] = {
#ifdef BUTTON_BACKLIGHT
  "/sys/class/leds/button-backlight/brightness",
#else
  "/sys/class/leds/so34-led0/brightness",
  "/sys/class/leds/so34-led1/brightness",
  "/sys/class/leds/so34-led2/brightness",
#endif
};

#else
char const*const RED_LED_FILE[] = {
 "/sys/class/leds/l-key-red/brightness",
 "/sys/class/leds/m-key-red/brightness",
 "/sys/class/leds/r-key-red/brightness",
 "/sys/class/leds/pwr-red/brightness",
};
char const*const GREEN_LED_FILE[]= {
 "/sys/class/leds/l-key-green/brightness",
 "/sys/class/leds/m-key-green/brightness",
 "/sys/class/leds/r-key-green/brightness",
 "/sys/class/leds/pwr-green/brightness",
};
char const*const BLUE_LED_FILE[] = {
 "/sys/class/leds/l-key-blue/brightness",
 "/sys/class/leds/m-key-blue/brightness",
 "/sys/class/leds/r-key-blue/brightness",
 "/sys/class/leds/pwr-blue/brightness",
};

char const*const RED_LED_FILE_TRIGGER[] = {
 "/sys/class/leds/l-key-red/trigger",
 "/sys/class/leds/m-key-red/trigger",
 "/sys/class/leds/r-key-red/trigger",
 "/sys/class/leds/pwr-red/trigger",
};

char const*const GREEN_LED_FILE_TRIGGER[] = {
 "/sys/class/leds/l-key-green/trigger",
 "/sys/class/leds/m-key-green/trigger",
 "/sys/class/leds/r-key-green/trigger",
 "/sys/class/leds/pwr-green/trigger",
};

char const*const BLUE_LED_FILE_TRIGGER[] = {
 "/sys/class/leds/l-key-blue/trigger",
 "/sys/class/leds/m-key-blue/trigger",
 "/sys/class/leds/r-key-blue/trigger",
 "/sys/class/leds/pwr-blue/trigger",
};

char const*const RED_LED_FILE_DELAYON[] = {
 "/sys/class/leds/l-key-red/delay_on",
 "/sys/class/leds/m-key-red/delay_on",
 "/sys/class/leds/r-key-red/delay_on",
 "/sys/class/leds/pwr-red/delay_on",
};

char const*const GREEN_LED_FILE_DELAYON[] = {
 "/sys/class/leds/l-key-green/delay_on",
 "/sys/class/leds/m-key-green/delay_on",
 "/sys/class/leds/r-key-green/delay_on",
 "/sys/class/leds/pwr-green/delay_on",
};

char const*const BLUE_LED_FILE_DELAYON[] = {
 "/sys/class/leds/l-key-blue/delay_on",
 "/sys/class/leds/m-key-blue/delay_on",
 "/sys/class/leds/r-key-blue/delay_on",
 "/sys/class/leds/pwr-blue/delay_on",
};

char const*const RED_LED_FILE_DELAYOFF[] = {
 "/sys/class/leds/l-key-red/delay_off",
 "/sys/class/leds/m-key-red/delay_off",
 "/sys/class/leds/r-key-red/delay_off",
 "/sys/class/leds/pwr-red/delay_off",
};

char const*const GREEN_LED_FILE_DELAYOFF[] = {
 "/sys/class/leds/l-key-green/delay_off",
 "/sys/class/leds/m-key-green/delay_off",
 "/sys/class/leds/r-key-green/delay_off",
 "/sys/class/leds/pwr-green/delay_off",
};

char const*const BLUE_LED_FILE_DELAYOFF[] = {
 "/sys/class/leds/l-key-blue/delay_off",
 "/sys/class/leds/m-key-blue/delay_off",
 "/sys/class/leds/r-key-blue/delay_off",
 "/sys/class/leds/pwr-blue/delay_off",
};

char const*const BUTTON_BACKLIGHT_FILE[] = {
 "/sys/class/leds/l-key-red/brightness",
 "/sys/class/leds/m-key-red/brightness",
 "/sys/class/leds/r-key-red/brightness",
 "/sys/class/leds/pwr-red/brightness",
 "/sys/class/leds/l-key-green/brightness",
 "/sys/class/leds/m-key-green/brightness",
 "/sys/class/leds/r-key-green/brightness",
 "/sys/class/leds/pwr-green/brightness",
 "/sys/class/leds/l-key-blue/brightness",
 "/sys/class/leds/m-key-blue/brightness",
 "/sys/class/leds/r-key-blue/brightness",
 "/sys/class/leds/pwr-blue/brightness",
};
#endif


char const*const LCD_BACKLIGHT_FILE = "/sys/class/leds/lcd-backlight/brightness";

char const*const ALS_FILE = "/sys/class/leds/lcd-backlight/device/als_enable";
