#include <Max3421e.h>
#include <Usb.h>
#include <AndroidAccessory.h>
#include <Log.h>

//////////////////////////////////////////
////// Constants
//////////////////////////////////////////
#define DEBUG                       true

#define MOTOR_1_PWMA_PIN            3
#define MOTOR_1_AIN1_PIN            4
#define MOTOR_1_AIN2_PIN            5
#define MOTOR_2_PWMB_PIN            8
#define MOTOR_2_BIN1_PIN            9
#define MOTOR_2_BIN2_PIN           10
#define MOTORS_STANDBY_PIN          7

#define MOTOR_1                     1
#define MOTOR_2                     2

#define COMMAND_CONTROL             1

#define ACTION_LEFT_STICK           1
#define ACTION_RIGHT_STICK          2
#define ACTION_STANDBY              3

#define INPUT_MIN                -100
#define INPUT_MAX                 100

#define THROTTLE_MIN             -255
#define THROTTLE_MAX              255

#define TURN_MIN                 -255
#define TURN_MAX                  255
//////////////////////////////////////////
////// Members
//////////////////////////////////////////
Log *_log;
unsigned long loop_start;
typedef struct _Control {
  int throttle;
  int turn;
  boolean standby;
}Control;


Control _control;
//////////////////////////////////////////
////// Initialization
//////////////////////////////////////////

/**
 * Called once when the Arduino first loads or resets 
 */
void onCreate() {
  _log = new Log(DEBUG); 
  
  pinMode(MOTOR_1_PWMA_PIN, OUTPUT);
  pinMode(MOTOR_1_AIN1_PIN, OUTPUT);
  pinMode(MOTOR_1_AIN2_PIN, OUTPUT);

  pinMode(MOTOR_2_PWMB_PIN, OUTPUT);
  pinMode(MOTOR_2_BIN1_PIN, OUTPUT);
  pinMode(MOTOR_2_BIN2_PIN, OUTPUT);
  
  pinMode(MOTORS_STANDBY_PIN, OUTPUT);  
    
  _control.throttle = 0;
  _control.turn = 0;
  _control.standby = HIGH; // disabled
}

//////////////////////////////////////////
////// Events
//////////////////////////////////////////

/**
 * Handle messages sent from the Android device
 *
 * @param command The command sent by the Android device
 * @param action The action sent by the Android device
 * @param dataLength The length of "data"
 * @param data Pointer to the extra data sent by the device
 */
void onMessageReceived(byte command, byte action, byte dataLength, byte* data) {
  switch (command) {
     case COMMAND_CONTROL:
        _log->d("Command Control");
        onCommandControl(action, dataLength, data);
        break; 
  }
}

/**
 * Handle control requests
 *
 * @param action The action sent by the Android device
 * @param dataLength The length of "data"
 * @param data Pointer to the extra data sent by the device
 */
void onCommandControl(byte action, byte dataLength, byte* data) {
  switch(action) {
    case ACTION_LEFT_STICK: {
      _log->d("Action Left Stick");
      int8_t rawThrottle = (int8_t) data[0];
      _log->d("Raw Throttle: ", rawThrottle);
      int targetThrottle = map(rawThrottle, INPUT_MIN, INPUT_MAX, THROTTLE_MIN, THROTTLE_MAX);
      _log->d("Throttle: ", targetThrottle);
      _control.throttle = targetThrottle;      
      break;
    }
    
    case ACTION_RIGHT_STICK: {
      _log->d("Action Right Stick");      
      int8_t rawTurn = (int8_t) data[1];
      _log->d("Raw Turn: ", rawTurn);
      int targetTurn = map(rawTurn, INPUT_MIN, INPUT_MAX, TURN_MIN, TURN_MAX);
      _log->d("Turn: ", targetTurn);
      _control.turn = targetTurn;
      break;
    }
    
    case ACTION_STANDBY: {
      _log->d("Action Standby");
      boolean standby = (boolean) data[0];
      _log->d("Standby: ", standby);
      if (standby) {
        _control.standby = LOW;
      } else {
        _control.standby = HIGH;
      }   
    }
  }     
}

//////////////////////////////////////////
////// Main loop
//////////////////////////////////////////

/**
 * Main loop. 
 * This method is called very frequently in an infinite loop
 */
void onLoop() {
  while(micros() - loop_start < 10000); // every 10 ms
  loop_start = micros();
  
  setMotorsStandby(_control.standby);
  setMotorPower(MOTOR_1, _control.throttle);
  setMotorPower(MOTOR_2, _control.turn);
}
















////
/////// Read any futher only if curious...
////


/**
 * Control motor power
 *
 * @param motor Either MOTOR_1 or MOTOR_2
 * @param speed Value between -255 and 255
 */
void setMotorPower(int motor, int speed) {
  boolean inPin1 = LOW;
  boolean inPin2 = HIGH;

  if(speed < 0){
    speed = -speed;
    inPin1 = HIGH;
    inPin2 = LOW;
  }

  if(motor == MOTOR_1){
    digitalWrite(MOTOR_1_AIN1_PIN, inPin1);
    digitalWrite(MOTOR_1_AIN2_PIN, inPin2);
    analogWrite(MOTOR_1_PWMA_PIN, speed);
  }else{
    digitalWrite(MOTOR_2_BIN1_PIN, inPin1);
    digitalWrite(MOTOR_2_BIN2_PIN, inPin2);
    analogWrite(MOTOR_2_PWMB_PIN, speed);
  }
}


/**
 * Change motors standby state
 *
 * @param state Either HIGH or LOW
 */
void setMotorsStandby(int state) {
  digitalWrite(MOTORS_STANDBY_PIN, state);
}












//////////////////////////////////////////
////// Android communication boilerplate 
//////////////////////////////////////////
#define BUFFER_SIZE                           16
#define TIME_STEP_BETWEEN_USB_RECONNECTIONS 1000 // in milliseconds

const char *USB_MANUFACTURER = "Amir Lazarovich";
const char *USB_MODEL        = "MotorDroid";
const char *USB_DESCRIPTION  = "Android powered RC toy car";
const char *USB_VERSION      = "1.0";
const char *USB_SITE         = "http://www.rcmotordroid.com";
const char *USB_SERIAL       = "0000000000000001";

                    
// command (1 byte), action (1 byte), data-length (1 byte), data (X bytes) 
AndroidAccessory *_acc;

// members-states
long _lastTimeReconnectedToUsb;



/**
 * Called once when the arduino first loads (or resets)
 */
void setup(){
  Serial.begin(9600);
  _acc = new AndroidAccessory(USB_MANUFACTURER,
                              USB_MODEL,
                              USB_DESCRIPTION,
                              USB_VERSION,
                              USB_SITE,
                              USB_SERIAL);
  _acc->powerOn();
  _lastTimeReconnectedToUsb = 0;
  
  onCreate();
}

/**
 * loop forever. 
 * Arduino calls this method in an infinite loop after returning from method "setup"
 */
void loop() {  
  if (_acc->isConnected()) {
    //Serial.println("reading...");   
    
    byte msg[BUFFER_SIZE];
    int len = _acc->read(msg, BUFFER_SIZE, 1);
    if (len > 0) {
      Serial.print("read: ");
      Serial.print(len, DEC);
      Serial.println(" bytes");

      handleMsgFromDevice(msg);
      sendAck();
    }
  } else if (_lastTimeReconnectedToUsb + TIME_STEP_BETWEEN_USB_RECONNECTIONS < millis()) {
    Serial.println("USB is not connected. Trying to reconnect...");
    reconnectUsb();
    _lastTimeReconnectedToUsb = millis();
  }
  
  onLoop();
}

/**
 * Handle messages coming from the Android device
 *
 * @param msg The raw payload 
 */
void handleMsgFromDevice(byte* msg) {
  byte command = msg[0];
  byte action = msg[1];
  byte dataLength = msg[2];
  printValues(command, action, dataLength);
  onMessageReceived(command, action, dataLength, msg + 3); 
}

/**
 * Try to reconnect to the Android device
 */
void reconnectUsb() {
  delete _acc;
  _acc = new AndroidAccessory(USB_MANUFACTURER,
                              USB_MODEL,
                              USB_DESCRIPTION,
                              USB_VERSION,
                              USB_SITE,
                              USB_SERIAL);
  _acc->powerOn();
}    

/**
 * Send acknowledge to connected Android device
 */ 
void sendAck() {
  if (_acc->isConnected()) {
    byte msg[1];
    msg[0] = 1;
    _acc->write(msg, 1);
  }  
}

/**
 * Print the command, action and data length to serial port 
 *
 * @param command The command sent by the Android device
 * @param action The action sent by the Android device
 * @param dataLength The length of the appended data field
 */
void printValues(byte command, byte action, byte dataLength) {
  Serial.print("Command: ");
  Serial.print(command, DEC);
  Serial.print(". Action: ");
  Serial.print(action, DEC);
  Serial.print(" Data Length: ");
  Serial.println(dataLength, DEC);
}
