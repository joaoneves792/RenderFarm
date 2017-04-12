#!/bin/bash
cd /home/ec2-user/RenderFarm
git stash && git pull
cd WebServer
make
make run