#!/usr/bin/env perl

use bignum;

$col = shift(@ARGV);
$min = shift(@ARGV);
$max = shift(@ARGV);
$binsize = shift(@ARGV);

$bins = 1 + int(($max-$min)/$binsize);
for($i=0; $i<$bins; $i++) {
	$bin[$i] = 0;
}

<>;

while(<>){
	chomp();
	@tokens = split(/,/);
	$x = $tokens[$col];
	$x=~ s/^\s+|\s+$//g;
	$x += 0;
	$val = $min;
	for($i=0; $i<$bins; $i++) {
		if ($x < $val) {
			$bin[$i]++;
			last;
		}
		$val += $binsize;
	}
}
$val = $min;
for($i=0; $i<$bins; $i++) {
	print("$val  $bin[$i]\n");
	$val += $binsize;
}
