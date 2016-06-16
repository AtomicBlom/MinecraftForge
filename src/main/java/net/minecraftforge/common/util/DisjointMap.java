package net.minecraftforge.common.util;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

/**
 * Created by rainwarrior on 5/31/16.
 */
public class DisjointMap<A, V> implements Iterable<Map.Entry<A, V>>
{
    private final Map<A, Pair<Optional<A>, Integer>> data = Maps.newHashMap();
    private final Map<A, V> rootToValue = Maps.newHashMap();
    private final IValueOps<? super A, V> ops;

    public static <A, V> DisjointMap<A, V> create(IValueOps<? super A, V> ops)
    {
        return new DisjointMap<A, V>(ops);
    }

    protected DisjointMap(IValueOps<? super A, V> ops)
    {
        this.ops = ops;
    }

    public boolean makeSet(A a)
    {
        if(!data.containsKey(a))
        {
            data.put(a, Pair.of(Optional.<A>absent(), 0));
            V value = ops.makeValue(a);
            rootToValue.put(a, value);
            return true;
        }
        return false;
    }

    public boolean contains(A a)
    {
        return data.containsKey(a);
    }

    public Optional<A> find(A a)
    {
        if(data.containsKey(a))
        {
            return Optional.of(findUnsafe(a));
        }
        return Optional.absent();
    }

    private A findUnsafe(A a)
    {
        Pair<Optional<A>, Integer> pair = data.get(a);
        if(pair.getLeft() == Optional.absent())
        {
            return a;
        }
        A root = findUnsafe(pair.getKey().get());
        data.put(a, Pair.of(Optional.of(root), pair.getRight()));
        return root;
    }

    public Optional<V> get(A a)
    {
        Optional<A> root = find(a);
        if(root.isPresent())
        {
            return Optional.of(rootToValue.get(root.get()));
        }
        return Optional.absent();
    }

    public boolean set(A key, V value)
    {
        Optional<A> keyRoot = find(key);
        if(keyRoot.isPresent())
        {
            V oldValue = rootToValue.get(keyRoot.get());
            rootToValue.put(keyRoot.get(), ops.unionValues(oldValue, value));
            return true;
        }
        return false;
    }

    public void union(A a, A b)
    {
        Optional<A> aR = find(a), bR = find(b);
        if(aR.isPresent() && bR.isPresent())
        {
            A aRoot = aR.get(), bRoot = bR.get();
            if(aRoot.equals(bRoot))
            {
                return;
            }
            Pair<Optional<A>, Integer> aData = data.get(aRoot);
            Pair<Optional<A>, Integer> bData = data.get(bRoot);
            int aRank = aData.getRight();
            int bRank = bData.getRight();
            A newRoot, oldRoot;
            if (aRank < bRank)
            {
                data.put(aRoot, Pair.of(Optional.of(bRoot), aRank));
                newRoot = bRoot;
                oldRoot = aRoot;
            }
            else if (bRank < aRank)
            {
                data.put(bRoot, Pair.of(Optional.of(aRoot), bRank));
                newRoot = aRoot;
                oldRoot = bRoot;
            }
            else
            {
                data.put(aRoot, Pair.of(Optional.of(bRoot), aRank));
                data.put(bRoot, Pair.of(bData.getLeft(), bRank + 1));
                newRoot = bRoot;
                oldRoot = aRoot;
            }
            V newValue = ops.unionValues(rootToValue.get(newRoot), rootToValue.get(oldRoot));
            rootToValue.remove(oldRoot);
            rootToValue.put(newRoot, newValue);
        }
        else
        {
            throw new IllegalStateException("one of the arguments isn't in the set: " + a + " or " + b);
        }
    }

    public boolean isSame(A a, A b)
    {
        Optional<A> aR = find(a), bR = find(b);
        if(aR.isPresent() && bR.isPresent())
        {
            return Objects.equal(aR.get(), bR.get());
        }
        return false;
    }

    public ImmutableMap<A, ImmutableSet<A>> toMap()
    {
        ImmutableMultimap.Builder<A, A> rootBuilder = ImmutableMultimap.builder();
        for (Map.Entry<A, Pair<Optional<A>, Integer>> entry : data.entrySet())
        {
            rootBuilder.put(findUnsafe(entry.getKey()), entry.getKey());
        }
        ImmutableMultimap<A, A> rootMap = rootBuilder.build();
        ImmutableMap.Builder<A, ImmutableSet<A>> builder = ImmutableMap.builder();
        for(Collection<A> as: rootMap.asMap().values())
        {
            ImmutableSet<A> set = ImmutableSet.copyOf(as);
            for(A a : set)
            {
                builder.put(a, set);
            }
        }
        return builder.build();
    }

    public DisjointMap<A, V> copy()
    {
        DisjointMap<A, V> ret = new DisjointMap<A, V>(ops);
        ret.data.putAll(data);
        for (Map.Entry<A, V> entry : rootToValue.entrySet())
        {
            ret.rootToValue.put(entry.getKey(), ops.copyValue(entry.getValue()));
        }
        return ret;
    }

    @Override
    public Iterator<Map.Entry<A, V>> iterator()
    {
        return Iterators.transform(data.entrySet().iterator(), new Function<Map.Entry<A,Pair<Optional<A>,Integer>>, Map.Entry<A, V>>()
        {
            public Map.Entry<A, V> apply(Map.Entry<A, Pair<Optional<A>, Integer>> input)
            {
                return new AbstractMap.SimpleEntry<A, V>(input.getKey(), rootToValue.get(findUnsafe(input.getKey())));
            }
        });
    }

    public Iterable<Map.Entry<A, A>> pairs()
    {
        return Iterables.transform(data.entrySet(), new Function<Map.Entry<A,Pair<Optional<A>,Integer>>, Map.Entry<A, A>>()
        {
            public Map.Entry<A, A> apply(Map.Entry<A, Pair<Optional<A>, Integer>> input)
            {
                return new AbstractMap.SimpleEntry<A, A>(input.getKey(), findUnsafe(input.getKey()));
            }
        });
    }

    /*public void addAll(DisjointMap<A, V> other)
    {
        // TODO: make more efficient?
        for (Map.Entry<A, V> entry : other)
        {
            A root = other.find(entry.getKey()).get();
            makeSet(entry.getKey());
            makeSet(root);
            union(entry.getKey(), root);
            set(root, entry.getValue());
        }
    }*/

    public static interface IValueOps<A, V>
    {
        V makeValue(A a);
        V unionValues(V v1, V v2);
        V copyValue(V v);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DisjointMap<?, ?> that = (DisjointMap<?, ?>) o;
        return Objects.equal(data, that.data) &&
        Objects.equal(rootToValue, that.rootToValue) &&
        Objects.equal(ops, that.ops);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(data, rootToValue, ops);
    }
}
