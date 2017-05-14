#!/bin/bash
cd /home/ec2-user/RenderFarm
sudo -u ec2-user git stash
sudo -u ec2-user git pull
cd LB
make clean
sudo -u ec2-user make
sudo -u ec2-user make run

