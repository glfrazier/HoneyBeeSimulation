#!/usr/bin/env bash

#
# Given a suite of simulations, output a series of (x,y) pairs
# in the form "x y", where the series of 'x' values was provided
# as input and the 'y' values are the equillibrium average of the
# specified field of the specified output file.
#

#
# arg 1: normalization: N (none), D (# domestic hives), F (# feral hives)
# arg 2: number of rows at the end of the file to average
# arg 3: the field # to average, '1' is the first field
# arg 4: the base pathname, with '%%' where the
#        5+ parameter is to replace it.
# arg 5+: the remaining arguments are the 'x' in the (x,y)
#         output of this program, and also the value to replace
#         the '%%' in the base pathname.
#

#
# EXAMPLE:
# tailaverager.sh 5 2 results/techreport00/expt4/prob_domestic=1.0,domestic_prob_swarm=%%/000/domesticDiedOfOldAge.csv \
#    0.0 0.1 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9 1.0

YisX=0

NORMALIZER=$1
if [[ ${NORMALIZER#Y=} != $NORMALIZER ]]; then
    YisX=1
    Y=${NORMALIZER#Y=}
    shift
    NORMALIZER=$1
fi
shift
NUM_ROWS=$1
shift
FIELD=$1
shift
PNAME=$1
shift

for x in $*; do
    pname=$(echo $PNAME | sed "s/\%\%/$x/")
    dname=$(dirname $pname)
    #
    # If NORMALIZER is "N", the average is divided by 1.
    # If NORMALIZER is "D", the average is divided by the number of domestic hives.
    # If NORMALIZER is "F", the average is divided by the number of feral hives.
    # 
    case $NORMALIZER in
        N) divisor=1 ;;
        D) divisor=$(head -n 1 ${dname}/domesticLiveHives.csv | awk '{ print $2; }') ;;
        F) divisor=$(head -n 1 ${dname}/feralLiveHives.csv | awk '{ print $2; }') ;;
    esac
    #
    # y <-- the average of the last NUM_ROWS of the field FIELD in the file PNAME, where the "%%"
    #       in PNAME is replaced by one of the sequence of values that were the last n arguments
    #       to this script. Note that the variable "$divisor" in the perl script is not escaped,
    #       so it is replaced by the value set in the case statement above.
    #
    y=$(tail -n $NUM_ROWS $pname | awk "{ print \$$FIELD; }" | perl -e "\$t=0;\$i=0;while(<>){\$t+=\$_;\$i++;}\$a=(\$t/\$i)/$divisor;print \"\$a\\n\";")
    if [[ $YisX == 1 ]]; then
        echo "$y $Y"
    else
        echo "$x $y"
    fi
done
