"""*******************************************************************************
 * Copyright (c) 2021 Ian Craggs
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    https://www.eclipse.org/legal/epl-2.0/
 * and the Eclipse Distribution License is available at 
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *    Ian Craggs - initial API and implementation and/or initial documentation
 *******************************************************************************"""


import paho.mqtt.client as mqtt
import time, sys

"""


"""
broker = "localhost"
port = 1883
host_application_id = "HOSTAPPID"

def control_on_message(client, userdata, msg):
    if msg.topic == "SPARKPLUG_TCK/RESULT":
        print("*** Result ***",  msg.payload)

def control_on_connect(client, userdata, flags, rc):
    print("Control client connected with result code "+str(rc))
    topic = sys.argv[1]
    payload = ' '.join(sys.argv[2:])
    print("publish topic %s payload %s" % (topic, payload))
    rc = client.publish(topic, payload)

publish_count = 0
def control_on_publish(client, userdata, mid):
    print("Control client published")
    global publish_count
    publish_count += 1

control_client = mqtt.Client("sparkplug_control")
control_client.on_connect = control_on_connect
#control_client.on_subscribe = control_on_subscribe
control_client.on_publish = control_on_publish
control_client.on_message = control_on_message
control_client.connect(broker, port)
control_client.loop_start()

# wait for publish to complete
while publish_count == 0:
    time.sleep(0.1)

control_client.loop_stop()




