unsortedList = [3,5,1,9,3,6,12]


def sort(A,i,j):
  if (j-i <= 1):
    return A
  low,high = i,j
  for x in range(i,j):
    if A[x] <= A[low]:
      low = x
  A[i], A[low] = A[low], A[i]
  for x in reversed(range(i,j)):
    if A[x] >= A[high]:
      high = x 
  A[j], A[high] = A[high], A[j]

  return sort(A,i+1,j-1)

print (sort(unsortedList,0,len(unsortedList)-1))
	
	



