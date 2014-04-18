import random

def test(arrayLength):
	lst = []
	for i in range(0,arrayLength):
		lst.append(random.randint(0,100))

	print(lst)
	mergesort(lst,0,arrayLength-1)
	print()
	print(lst)



def mergesort(A,l,r):
	#print ("l: " + str(l) + " r: " + str(r))
	#rw_input()
	if (l < r):
		m = (l + r) / 2
		mergesort(A,l,m)
		mergesort(A,m+1,r)
		merge(A,l,m,r)


def merge(A,l,m,r):
	print (str(l) + " " + str(m) + " " + str(r))
	B = A[l:m+1]
	B.extend(A[m:r+1].reverse())
	p = l
	q = r
	for i in range(l,r):

		if B[p] <= B[q]:
			A[i] = B[p]
			p += 1
		else:
			A[i] = B[q]
			q -= 1

test(4)