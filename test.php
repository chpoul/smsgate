<?php
/* 
 * Example PHP-file, that demonstrates communicating with the SMSgate app.
 */

$KEY = $_POST['KEY']; //STRING
$SEND = $_POST['SEND']; //STRING (true|false)
$FROM = $_POST['FROM']; //STRING
$MSG = $_POST['MSG']; //STRING
$ID = $_POST['ID']; //STRING

$SECRET = "TESTKEY";

// If the key doesn't match, don't do anything.
if ($KEY == $SECRET) {
  if ($SEND == "true") {//Send confirmed
    echo "SMS with id ".$ID." was send!";
  }
  else if ($FROM != null && $FROM != "") {//Receiving message
    echo "Message from ".$FROM.": ".$MSG;
  }
  else {//Return messages to send
    echo '[{"ID":"123","NO":"+4512345678","MSG":"Test message..."},{"ID":"124","NO":"12345678","MSG":"Test message..."}]';
  }
}

?>
