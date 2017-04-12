#!/bin/bash
git stash && git pull
cd WebServer
make
make run