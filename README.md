# SMSgate

SMSgate is an Android app that sends SMS-messages from JSON data fetched from a URL. It also forwards received SMS-messages to the same URL, as POST requests. It is intended to use as a way to send and receive SMS-messages from webpages.

-----
Messages are fetched by a POST request, which should return a JSON array:

POST: KEY=<key>

Return example: [{"ID":"<message id>","NO":"<recipient no.>","MSG":"<message>"},{"ID":"123","NO":"+4512345678","MSG":"Test message..."},{"ID":"124","NO":"12345678","MSG":"Test message..."}]

-----
Sending a message is confirmed to the URL, by a POST request:

POST: KEY=<key>&SEND=true&ID=<messageID>

-----
When an SMS-message is received, it is forwarded to the URL by a POST request:

POST: KEY=<key>&FROM=<sender no.>&MSG=<message>


