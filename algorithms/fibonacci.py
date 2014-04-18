import sys
n = int(sys.argv[1])

called = 0

def fib_rec(n):
	global called
	called += 1
	if n==1 or n==2:
		return 1
	else:
		return fib_rec(n-1) + fib_rec(n-2)

def fib_it(n):
	if n==1 or n==2:
		return 1
	n1 = 1
	n2 = 1

	for x in range(3,n+1):
		n3 = n1 + n2
		n1 = n2
		n2 = n3
	
	return n2

print (fib_rec(n))
print (called)
print (fib_it(n))

