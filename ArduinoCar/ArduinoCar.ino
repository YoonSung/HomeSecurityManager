int MOTOR_A_DIR = 12;
int MOTOR_A_BRAKE = 9;
int MOTOR_A_PWM = 3;

int MOTOR_B_DIR = 13;
int MOTOR_B_BRAKE = 8;
int MOTOR_B_PWM = 11;

// Maximum MOTOR_SPEED is 255
int MOTOR_SPEED = 255;

void setup() {
  Serial.begin(9600);

  pinMode(MOTOR_A_DIR, OUTPUT);
  pinMode(MOTOR_A_BRAKE, OUTPUT);

  pinMode(MOTOR_B_DIR, OUTPUT);
  pinMode(MOTOR_B_BRAKE, OUTPUT);
}

char message = 0;
char reverse = 0;
void loop() {
  if (Serial.available()) {
    message = Serial.read();
    Serial.println(message);

    switch (message) {
    case 'W':
    case 'w':
      reverse = 0;
      digitalWrite(MOTOR_A_DIR, HIGH);
      digitalWrite(MOTOR_A_BRAKE, LOW);
      analogWrite(MOTOR_A_PWM, MOTOR_SPEED);

      digitalWrite(MOTOR_B_DIR, HIGH);
      digitalWrite(MOTOR_B_BRAKE, LOW);
      analogWrite(MOTOR_B_PWM, MOTOR_SPEED);
      break;
    case 'A':
    case 'a':
      digitalWrite(MOTOR_A_DIR, reverse ? LOW : HIGH);
      digitalWrite(MOTOR_A_BRAKE, LOW);
      analogWrite(MOTOR_A_PWM, MOTOR_SPEED);

      digitalWrite(MOTOR_B_DIR, reverse ? LOW : HIGH);
      digitalWrite(MOTOR_B_BRAKE, LOW);
      analogWrite(MOTOR_B_PWM, MOTOR_SPEED / 2);
      break;
    case 'D':
    case 'd':
      digitalWrite(MOTOR_A_DIR, reverse ? LOW : HIGH);
      digitalWrite(MOTOR_A_BRAKE, LOW);
      analogWrite(MOTOR_A_PWM, MOTOR_SPEED / 2);

      digitalWrite(MOTOR_B_DIR, reverse ? LOW : HIGH);
      digitalWrite(MOTOR_B_BRAKE, LOW);
      analogWrite(MOTOR_B_PWM, MOTOR_SPEED);
      break;
    case 'S':
    case 's':
      reverse = 1;
      digitalWrite(MOTOR_A_DIR, LOW);
      digitalWrite(MOTOR_A_BRAKE, LOW);
      analogWrite(MOTOR_A_PWM, MOTOR_SPEED);

      digitalWrite(MOTOR_B_DIR, LOW);
      digitalWrite(MOTOR_B_BRAKE, LOW);
      analogWrite(MOTOR_B_PWM, MOTOR_SPEED);
      break;
    default:
      digitalWrite(MOTOR_A_BRAKE, HIGH);
      digitalWrite(MOTOR_B_BRAKE, HIGH);
      break;
    }
    
    delay(300);
      digitalWrite(MOTOR_A_BRAKE, HIGH);
      digitalWrite(MOTOR_B_BRAKE, HIGH);
  }
}

